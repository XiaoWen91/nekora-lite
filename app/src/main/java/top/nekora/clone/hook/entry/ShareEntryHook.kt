package top.nekora.clone.hook.entry

import android.content.Context
import android.widget.Toast
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.util.Log
import top.nekora.clone.util.MediaDownloader
import top.nekora.clone.util.Reflect
import java.util.Collections
import java.util.WeakHashMap

/**
 * 分享面板三个入口（完整还原原版 sw.java [KwaiSharePanel]）。
 *
 * 原版三个按钮（图标名与功能）：
 *   share_entry_module_icon_v4.png          → 模块设置
 *   share_entry_parse_download_icon_v1.png  → 解析下载（保存视频/图片/live图）
 *   share_entry_video_info_icon_v1.png      → 视频信息
 *
 * 原版有 2 个 hook：
 *   hook 1：面板类里「无参 + 返回 void + 名字 execute」的方法
 *   hook 2：返回类型是 ShareInitResponse$SharePanelData 的方法
 *
 * 插入方式：复制列表里第一个元素当模板，深拷贝后改 id/title，
 * 这样必填字段都有合法值，宿主一定能渲染。
 */
object ShareEntryHook {

    private const val TAG = "[SharePanel]"

    private const val CLS_ELEMENT =
        "com.kwai.sharelib.model.ShareInitResponse\$SharePanelElement"

    private val PANEL_CLASSES = listOf(
        "com.yxcorp.gifshow.share.widget.ForwardGridSectionFragment",
        "com.yxcorp.gifshow.share.growth.immerse.ImmerseSharePanelFragment",
    )

    /** 三个按钮的定义 */
    private data class Entry(val key: String, val title: String, val icon: String, val action: String)

    private val ENTRIES = listOf(
        Entry("nekora_entry_module", "模块设置",
            "share_entry_module_icon_v4.png", "module"),
        Entry("nekora_entry_download", "解析下载",
            "share_entry_parse_download_icon_v1.png", "download"),
        Entry("nekora_entry_videoinfo", "视频信息",
            "share_entry_video_info_icon_v1.png", "videoinfo"),
    )

    private val handled = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    /** 当前分享的作品快照，供下载用 */
    @Volatile
    private var currentSnapshot: Any? = null

    /** 记住宿主 ClassLoader */
    fun bindLoader(cl: ClassLoader) {
        hostLoader = cl
    }

    @Volatile
    private var hostLoader: ClassLoader? = null

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        hostLoader = loader
        if (!Prefs.getBool("share_entry_enabled", true)) return

        var h1 = 0
        var h2 = 0
        for (name in PANEL_CLASSES) {
            val cls = Reflect.findClass(loader, name) ?: continue
            h1 += hookExecute(module, cls, pkg)
            h2 += hookDataMethods(module, cls, pkg)
        }
        Log.hooked("$TAG install pkg=$pkg (execute=$h1 data=$h2, entries=${ENTRIES.size})")
        if (h1 + h2 == 0) Log.w("$TAG 未找到分享面板类")
    }

    // ==================== hook 1：execute ====================

    /**
     * hook 1：面板装配时机。
     *
     * 原版找名字为 "execute" 的方法，但这个版本的快手把它混淆了
     * （日志显示 execute=0）。所以这里放宽：
     *   - 名字含 "execute" / "bind" / "build" / "init" 的无参 void 方法
     *   - 所有无参且返回 List/Collection/ShareInit 的方法
     */
    private fun hookExecute(module: XposedModule, cls: Class<*>, pkg: String): Int {
        var n = 0
        for (m in cls.declaredMethods) {
            if (m.parameterTypes.isNotEmpty()) continue
            if (java.lang.reflect.Modifier.isStatic(m.modifiers)) continue

            val nameLower = m.name.lowercase()
            val rt = m.returnType
            val isVoid = rt == Void.TYPE
            val isCollection = List::class.java.isAssignableFrom(rt) ||
                Collection::class.java.isAssignableFrom(rt)
            val isData = rt.name.contains("ShareInit") || rt.name.contains("SharePanelData")

            val nameMatch = nameLower.contains("execute") || nameLower.contains("bind") ||
                nameLower.contains("build") || nameLower.contains("init") ||
                nameLower.contains("setup") || nameLower.contains("refresh")

            // 只挂可能是装配时机的方法
            if (!(isVoid && nameMatch) && !isCollection && !isData) continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    val r = chain.proceed()
                    // 两种时机都试：thisObject 里的列表，或返回值本身
                    runCatching { insertAll(chain.thisObject, pkg) }
                    if (r != null) runCatching { insertAll(r, pkg) }
                    r
                }
                n++
                Log.i("$TAG hooked ${cls.simpleName}.${m.name} -> ${rt.simpleName}")
            }.onFailure { Log.w("$TAG hook ${m.name}: ${it.message}") }
        }
        return n
    }

    // ==================== hook 2：数据方法 ====================

    private fun hookDataMethods(module: XposedModule, cls: Class<*>, pkg: String): Int {
        var n = 0
        for (m in cls.declaredMethods) {
            val rt = m.returnType.name
            if (!rt.contains("SharePanelData") && !rt.contains("ShareInit")) continue
            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    val result = chain.proceed()
                    // 顺便记下当前作品（供下载用）
                    runCatching { captureSnapshot(chain.thisObject) }
                    runCatching { insertAll(result, pkg) }
                        .onFailure { Log.w("$TAG insert(data): ${it.message}") }
                    result
                }
                n++
                Log.i("$TAG hooked ${cls.simpleName}.${m.name}")
            }.onFailure { Log.w("$TAG data hook: ${it.message}") }
        }
        return n
    }

    /** 记录当前分享的作品对象 */
    private fun captureSnapshot(panel: Any?) {
        if (panel == null) return
        for (name in arrayOf("mPhoto", "photo", "mSnapshot", "snapshot", "feed", "mFeed")) {
            val f = Reflect.findField(panel.javaClass, name) ?: continue
            runCatching {
                f.isAccessible = true
                f.get(panel)?.let { currentSnapshot = it }
            }
            if (currentSnapshot != null) return
        }
    }

    // ==================== 插入三个按钮 ====================

    private fun insertAll(owner: Any?, pkg: String) {
        if (owner == null) return
        if (!handled.add(owner)) return

        val list = findEntryList(owner) ?: run {
            Log.i("$TAG ${owner.javaClass.simpleName} 上没有可插入的列表")
            return
        }

        // 用第一个元素当模板
        val template = list.firstOrNull { it != null }
        if (template == null) {
            Log.w("$TAG 列表为空，无模板可用")
            return
        }

        var added = 0
        for (entry in ENTRIES) {
            if (list.any { isOurs(it, entry.key) }) continue

            val copy = deepCopy(template) ?: continue
            renameAll(copy, entry.key, entry.title)
            attachAction(owner, copy, entry)

            runCatching {
                list.add(copy)
                added++
                Log.i("$TAG ★ 已插入「${entry.title}」")
            }
        }
        Log.i("$TAG 插入完成：$added 个，列表 size=${list.size}")
    }

    /** 深拷贝：所有字段原样复制 */
    private fun deepCopy(src: Any): Any? = runCatching {
        val cls = src.javaClass
        val ctor = cls.declaredConstructors.minByOrNull { it.parameterTypes.size }
            ?: return@runCatching null
        ctor.isAccessible = true
        val args = Array(ctor.parameterTypes.size) { defaultOf(ctor.parameterTypes[it]) }
        val dst = ctor.newInstance(*args) ?: return@runCatching null

        var c: Class<*>? = cls
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                if (java.lang.reflect.Modifier.isFinal(f.modifiers)) continue
                runCatching {
                    f.isAccessible = true
                    f.set(dst, f.get(src))
                }
            }
            c = c.superclass
        }
        dst
    }.getOrNull()

    /** 按字段名改标识与标题 */
    private fun renameAll(obj: Any, key: String, title: String) {
        var c: Class<*>? = obj.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (f.type != String::class.java) continue
                val fn = f.name.lowercase()
                runCatching {
                    f.isAccessible = true
                    when {
                        fn == "id" || fn == "mid" || fn == "elementid"
                                || fn == "elementkey" || fn == "key" -> f.set(obj, key)
                        fn == "title" || fn == "name" || fn == "mtitle"
                                || fn == "elementtitle" || fn == "text" -> f.set(obj, title)
                    }
                }
            }
            c = c.superclass
        }
    }

    /** 挂点击行为：找 action / listener 字段并替换 */
    private fun attachAction(panel: Any, entry: Any, def: Entry) {
        // 找 OnClickListener 类型字段
        var c: Class<*>? = entry.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (!android.view.View.OnClickListener::class.java.isAssignableFrom(f.type)) continue
                runCatching {
                    f.isAccessible = true
                    f.set(entry, android.view.View.OnClickListener { v ->
                        dispatch(def, v.context, panel)
                    })
                    return@attachAction
                }
            }
            c = c.superclass
        }
        Log.w("$TAG 「${def.title}」没有 OnClickListener 字段，点击可能无效")
    }

    /** 三个按钮的实际行为 */
    private fun dispatch(def: Entry, ctx: Context?, panel: Any) {
        Log.i("$TAG 点击「${def.title}」")
        when (def.action) {
            "module" -> {
                if (ctx != null) PanelHost.showForComment(ctx)
            }
            "download" -> doDownload(ctx, def.title)
            "videoinfo" -> showVideoInfo(ctx, panel)
        }
    }

    /** 解析下载：提取地址并下载 */
    private fun doDownload(ctx: Context?, label: String) {
        if (ctx == null) return
        val snap = currentSnapshot
        if (snap == null) {
            toast(ctx, "未获取到作品信息")
            return
        }
        val urls = MediaDownloader.extractUrls(snap)
        if (urls.isEmpty()) {
            toast(ctx, "暂未获取到媒体地址")
            return
        }
        val type = if (urls.size > 1) "图片" else "视频"
        toast(ctx, "开始下载 $type…")
        MediaDownloader.download(ctx, urls, type, "nekora") { r ->
            val msg = if (r.ok == r.total) "已保存 ${r.ok} 个${r.type}"
            else if (r.ok > 0) "已保存 ${r.ok}/${r.total} 个${r.type}"
            else "${r.type} 下载失败"
            toast(ctx, msg)
            Log.i("$TAG 下载完成 ok=${r.ok}/${r.total} type=${r.type} dir=${r.dir}")
        }
    }

    /** 视频信息：解析并展示 */
    private fun showVideoInfo(ctx: Context?, panel: Any) {
        if (ctx == null) return
        val snap = currentSnapshot ?: panel
        val info = buildString {
            append("作品信息\n")
            for (name in arrayOf("mPhotoId", "photoId", "mCaption", "caption", "mUserId", "mUserName")) {
                val v = Reflect.getFieldValue(snap, name) ?: continue
                if (v is String && v.isNotEmpty()) append("\n$name: $v")
            }
            val urls = MediaDownloader.extractUrls(snap)
            append("\n\n媒体地址 (${urls.size}):")
            urls.take(3).forEach { append("\n${it.take(80)}…") }
        }
        Log.i("$TAG 视频信息:\n$info")
        toast(ctx, "视频信息已输出到日志")
    }

    private fun toast(ctx: Context, msg: String) {
        runCatching {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 工具 ====================

    @Suppress("UNCHECKED_CAST")
    private fun findEntryList(owner: Any): MutableList<Any?>? {
        var c: Class<*>? = owner.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (!List::class.java.isAssignableFrom(f.type)) continue
                val n = f.name.lowercase()
                if (n.contains("listener") || n.contains("callback")) continue
                runCatching {
                    f.isAccessible = true
                    val v = f.get(owner) as? MutableList<Any?> ?: return@runCatching
                    if (looksLikeElements(v, f.name)) return v
                }
            }
            c = c.superclass
        }
        return null
    }

    private fun looksLikeElements(list: MutableList<*>, fieldName: String): Boolean {
        if (list.isEmpty()) {
            val n = fieldName.lowercase()
            return n.contains("area") || n.contains("element") ||
                n.contains("item") || n.contains("grid") || n.contains("share")
        }
        val first = list.firstOrNull() ?: return false
        val cn = first.javaClass.name
        return cn.contains("SharePanelElement") || cn.contains("ShareInitArea") || cn == CLS_ELEMENT
    }

    private fun isOurs(item: Any?, key: String): Boolean {
        if (item == null) return false
        for (name in arrayOf("id", "mId", "elementId", "elementKey", "key")) {
            val v = Reflect.getFieldValue(item, name)
            if (v is String && v == key) return true
        }
        return false
    }

    private fun defaultOf(t: Class<*>): Any? = when (t) {
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Boolean::class.javaPrimitiveType -> false
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }
}
