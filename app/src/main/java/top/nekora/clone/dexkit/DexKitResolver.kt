package top.nekora.clone.dexkit

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.result.ClassData
import top.nekora.clone.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * DexKit 动态符号解析（带缓存）。
 *
 * 关键设计（按原版 C0190 的做法修正）：
 *   原版不是用 apk 路径，而是先把 dex 读成 byte[][] 再 create：
 *     DexKitBridge.create(byte[][])
 *   原因：宿主沙箱进程里 apk 路径可能不可读，但内存里的 dex 一定能拿到。
 *
 *   本实现三级降级：
 *     1. ClassLoader 方式（最省事，dex 全）
 *     2. apk 路径方式
 *     3. byte[][] 方式（从 ClassLoader 的 dex 文件读）
 */
object DexKitResolver {

    private const val TAG = "[DexKit]"

    private var bridge: DexKitBridge? = null
    private var loader: ClassLoader = DexKitResolver::class.java.classLoader!!

    private val classCache = ConcurrentHashMap<String, Class<*>>()
    private val missCache = ConcurrentHashMap.newKeySet<String>()
    private var initialized = false

    var scanned = false
        private set

    /** 初始化失败的具体原因，供日志输出 */
    var lastError: String = ""
        private set

    fun bindLoader(cl: ClassLoader) {
        loader = cl
    }

    // 待扫描上下文（进面板时才用）
    private var pendLoader: ClassLoader? = null
    private var pendApk: String? = null
    private var pendPkg: String = ""
    private var pendVer: String = ""

    /** 只记录上下文，不扫 dex —— 与原版一致，等用户点「本机查找」才真正开扫 */
    fun prepare(cl: ClassLoader, apkPath: String?, pkg: String, ver: String) {
        pendLoader = cl
        pendApk = apkPath
        pendPkg = pkg
        pendVer = ver
        loader = cl
    }

    /** 面板里点「本机查找」时调用 */
    fun ensureScanned(): Boolean {
        if (isReady()) return true
        val cl = pendLoader ?: return false
        return init(cl, pendApk, pendPkg, pendVer)
    }

    /** 缓存是否已就绪（面板用来决定弹不弹查找对话框） */
    fun hasCache(): Boolean {
        val apk = pendApk ?: return false
        val mt = runCatching { File(apk).lastModified() }.getOrDefault(0L)
        return mt > 0 && DexCache.isValid(pendVer, apk, mt)
    }

    fun init(
        hostLoader: ClassLoader,
        apkPath: String?,
        hostPkg: String,
        hostVersion: String,
    ): Boolean {
        loader = hostLoader
        if (initialized) return isReady()
        initialized = true

        Log.i("$TAG init start: apk=$apkPath ver=$hostVersion")

        // ★ 关键：DexKit 2.x 不会自动加载 native 库，必须手动 loadLibrary
        if (!NativeLib.loaded) {
            val ok = NativeLib.load()
            Log.i("$TAG native lib load=$ok")
            if (!ok) {
                lastError = NativeLib.lastError
                Log.w("$TAG native 库加载失败，DexKit 不可用: $lastError")
                return false
            }
        }

        // 缓存命中直接返回
        val mtime = apkPath?.let { runCatching { File(it).lastModified() }.getOrDefault(0L) } ?: 0L
        if (apkPath != null && mtime > 0 && DexCache.isValid(hostVersion, apkPath, mtime)) {
            Log.i("$TAG cache HIT, skip scan")
            scanned = false
            return true
        }

        // 三级降级尝试
        val ok = tryCreate() != null
        scanned = ok

        if (ok) {
            Log.i("$TAG init OK")
            if (apkPath != null && mtime > 0) {
                runCatching { DexCache.beginWrite(hostVersion, apkPath, mtime) }
            }
        } else {
            Log.w("$TAG init FAILED: $lastError")
        }
        return ok
    }

    private fun tryCreate(): DexKitBridge? {
        // 方式 1：ClassLoader
        runCatching {
            Log.i("$TAG try ClassLoader…")
            val b = DexKitBridge.create(loader, true)
            if (b.isValid) {
                Log.i("$TAG ClassLoader OK")
                bridge = b
                return b
            }
            runCatching { b.close() }
            lastError = "ClassLoader returned invalid"
        }.onFailure { lastError = "ClassLoader: ${it.javaClass.simpleName}: ${it.message}" }
        Log.w("$TAG ClassLoader failed: $lastError")

        // 方式 2：apk 路径
        runCatching {
            val path = apkPathOf(loader)
            if (path != null) {
                Log.i("$TAG try apk path: $path")
                val b = DexKitBridge.create(path)
                if (b.isValid) {
                    Log.i("$TAG apk path OK")
                    bridge = b
                    return b
                }
                runCatching { b.close() }
                lastError = "apkPath returned invalid"
            }
        }.onFailure { lastError = "apkPath: ${it.javaClass.simpleName}: ${it.message}" }
        Log.w("$TAG apk path failed: $lastError")

        // 方式 3：byte[][]
        runCatching {
            val dexFiles = dexFilesOf(loader)
            Log.i("$TAG try byte[][], found ${dexFiles.size} dex file(s)")
            if (dexFiles.isNotEmpty()) {
                val arrays = dexFiles.mapNotNull { f ->
                    runCatching { f.readBytes() }.getOrNull()
                }.toTypedArray()
                if (arrays.isNotEmpty()) {
                    val b = DexKitBridge.create(arrays)
                    if (b.isValid) {
                        Log.i("$TAG byte[][] OK (${arrays.size} dex)")
                        bridge = b
                        return b
                    }
                    runCatching { b.close() }
                    lastError = "byte[][] returned invalid"
                }
            }
        }.onFailure { lastError = "byte[][]: ${it.javaClass.simpleName}: ${it.message}" }
        Log.w("$TAG byte[][] failed: $lastError")

        return null
    }

    /** 从 BaseDexClassLoader 反射拿 apk 路径 */
    private fun apkPathOf(cl: ClassLoader): String? {
        return runCatching {
            var c: Class<*>? = cl.javaClass
            while (c != null) {
                try {
                    val f = c.getDeclaredField("pathList").apply { isAccessible = true }
                    val pathList = f.get(cl) ?: break
                    val df = pathList.javaClass.getDeclaredField("dexElements")
                        .apply { isAccessible = true }
                    val elements = df.get(pathList) as? Array<*> ?: break
                    val first = elements.firstOrNull() ?: break
                    val dexF = first.javaClass.getDeclaredField("dexFile")
                        .apply { isAccessible = true }
                    val dexFile = dexF.get(first)
                    val getPath = dexFile?.javaClass?.getMethod("getPath")
                    return getPath?.invoke(dexFile) as? String
                } catch (_: NoSuchFieldException) {
                    c = c.superclass
                }
            }
            null
        }.getOrNull()
    }

    /** 列出 ClassLoader 里所有 dex 文件 */
    private fun dexFilesOf(cl: ClassLoader): List<File> {
        val out = LinkedHashSet<File>()
        runCatching {
            var c: Class<*>? = cl.javaClass
            while (c != null) {
                try {
                    val f = c.getDeclaredField("pathList").apply { isAccessible = true }
                    val pathList = f.get(cl) ?: break
                    val df = pathList.javaClass.getDeclaredField("dexElements")
                        .apply { isAccessible = true }
                    val elements = df.get(pathList) as? Array<*> ?: break
                    for (el in elements) {
                        if (el == null) continue
                        runCatching {
                            val dexF = el.javaClass.getDeclaredField("dexFile")
                                .apply { isAccessible = true }
                            val dexFile = dexF.get(el) ?: return@runCatching
                            val getPath = dexFile.javaClass.getMethod("getPath")
                            val path = getPath.invoke(dexFile) as? String
                            if (path != null) {
                                val file = File(path)
                                if (file.exists() && file.canRead()) out.add(file)
                            }
                        }
                    }
                    break
                } catch (_: NoSuchFieldException) {
                    c = c.superclass
                }
            }
        }
        return out.toList()
    }

    fun isReady(): Boolean = runCatching { bridge?.isValid == true }.getOrDefault(false)

    // ==================== 查找 ====================

    fun findClass(key: String, vararg queries: (DexKitBridge) -> List<ClassData>): Class<*>? {
        // Read only implementation 是 DexKit 的 DSL 在某些状态下抛的，
        // 这里统一兜住，避免整个扫描中断
        classCache[key]?.let { return it }

        // ★ 优先查静态符号表（类名直连，不需要 DexKit）
        SymbolTable.find(key, loader)?.let {
            classCache[key] = it
            return it
        }

        if (key in missCache) return null

        DexCache.get(key)?.let { name ->
            val cls = runCatching { Class.forName(name, false, loader) }.getOrNull()
            if (cls != null) {
                classCache[key] = cls
                Log.i("$TAG $key <- cache: $name")
                return cls
            }
        }

        val b = bridge
        if (b == null || !b.isValid) {
            Log.w("$TAG $key skipped: dexkit not ready")
            return null
        }

        for ((idx, q) in queries.withIndex()) {
            val list = runCatching { q(b) }.getOrElse { t ->
                val msg = t.message ?: t.javaClass.simpleName
                Log.w("$TAG $key query#$idx 失败: $msg")
                // Read only implementation = 这个 query 的 matcher 写法不被当前版本接受，
                // 换下一条规则继续
                emptyList()
            }
            if (list.isEmpty()) continue
            for (data in list) {
                val cls = runCatching { data.getInstance(loader) }.getOrNull() ?: continue
                classCache[key] = cls
                DexCache.put(key, cls.name)
                Log.i("$TAG $key -> ${cls.name} (query#$idx)")
                return cls
            }
        }
        missCache.add(key)
        Log.w("$TAG $key NOT FOUND")
        return null
    }

    // ==================== 具体规则 ====================

    fun settingsItemListClass(): Class<*>? = findClass(
        "settings.itemList",
        // 先全量搜类名，不限定包（searchPackages 在部分版本会抛 Read only implementation）
        { b ->
            b.findClass(
                FindClass.create()
                    .matcher(
                        ClassMatcher.create()
                            .className("SettingItemListStencilFragment", StringMatchType.Contains, false)
                    )
            )
        },
        { b ->
            b.findClass(
                FindClass.create()
                    .matcher(
                        ClassMatcher.create()
                            .className("SettingItemListFragment", StringMatchType.Contains, false)
                    )
            )
        },
    )

    fun commentRootClass(): Class<*>? = findClass(
        "comment.root",
        { b ->
            b.findClass(
                FindClass.create()
                    .matcher(
                        ClassMatcher.create()
                            .className("CommentRootConstraintLayout", StringMatchType.Contains, false)
                    )
            )
        },
        { b ->
            b.findClass(
                FindClass.create()
                    .matcher(
                        ClassMatcher.create()
                            .superClass(
                                "androidx.constraintlayout.widget.ConstraintLayout",
                                StringMatchType.Contains, false
                            )
                            .usingStrings("comment")
                    )
            )
        },
    )

    fun bottomBarClass(): Class<*>? = findClass(
        "bottombar",
        { b ->
            b.findClass(
                FindClass.create()
                    .matcher(
                        ClassMatcher.create()
                            .className("BottomActionBar", StringMatchType.Contains, false)
                    )
            )
        },
    )

    fun sharePanelClass(): Class<*>? = findClass(
        "share.panel",
        { b ->
            b.findClass(
                FindClass.create()
                    .matcher(
                        ClassMatcher.create()
                            .className("SharePanelFragment", StringMatchType.Contains, false)
                    )
            )
        },
        { b ->
            b.findClass(
                FindClass.create()
                    .matcher(
                        ClassMatcher.create()
                            .className("ForwardGridSectionFragment", StringMatchType.Contains, false)
                    )
            )
        },
    )

    fun tabModelClass(): Class<*>? = findClass(
        "topbar.tab",
        { b ->
            b.findClass(
                FindClass.create()
                    .matcher(
                        ClassMatcher.create()
                            .className("TabModel", StringMatchType.Contains, false)
                    )
            )
        },
    )

    fun warmUp() {
        if (!scanned) {
            Log.i("$TAG warmUp skipped (no scan)")
            return
        }
        runCatching {
            settingsItemListClass()
            commentRootClass()
            bottomBarClass()
            sharePanelClass()
            tabModelClass()
            Log.i("$TAG warmUp done")
        }
    }

    fun close() {
        runCatching { bridge?.close() }
        bridge = null
        initialized = false
    }
}
