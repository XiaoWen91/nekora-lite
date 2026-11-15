package top.nekora.clone.dexkit

import android.content.pm.ApplicationInfo
import top.nekora.clone.util.Log
import java.io.File

/**
 * DexKit native 库加载器。
 *
 * 问题：宿主进程里 `System.loadLibrary("dexkit")` 只会在**宿主自己的**
 * nativeLibraryDir 里找，找不到模块 APK 里的 libdexkit.so，
 * 所以抛 UnsatisfiedLinkError。
 *
 * 修法：LSPosed 的 XposedModule 提供 getModuleApplicationInfo()，
 * 里面就有模块自己的 nativeLibraryDir 和 sourceDir。
 *
 * 加载顺序：
 *   1. 模块 nativeLibraryDir/libdexkit.so   ← 最直接
 *   2. 从模块 APK 解压到私有目录再 load      ← 兜底
 *   3. System.loadLibrary                   ← 最后试
 */
object NativeLib {

    private const val TAG = "[NativeLib]"
    private const val LIB = "libdexkit.so"

    @Volatile
    var loaded = false
        private set

    @Volatile
    var lastError = ""
        private set

    /** 由 ModuleEntry 在 onPackageReady 时传入 */
    private var moduleInfo: ApplicationInfo? = null

    fun bindModule(info: ApplicationInfo?) {
        moduleInfo = info
        Log.i("$TAG module info: nativeDir=${info?.nativeLibraryDir} srcDir=${info?.sourceDir}")
    }

    /**
     * 备用：直接从 PackageManager 查模块自己的 ApplicationInfo。
     * host 进程里 moduleApplicationInfo 有时为 null，用 PM 更可靠。
     */
    private fun queryModuleInfoViaPM(): ApplicationInfo? = runCatching {
        val ctx = appContext() ?: return@runCatching null
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        pm.getApplicationInfo(MODULE_PKG, 0)
    }.onFailure {
        Log.w("$TAG PM 查询模块信息失败: ${it.message}")
    }.getOrNull()

    private fun appContext(): android.content.Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication").invoke(null) as? android.content.Context
    }.getOrNull()

    private val MODULE_PKG = "top.nekora.clone"

    fun load(): Boolean {
        if (loaded) return true
        lastError = ""

        // 1. 模块 nativeLibraryDir（最可靠）
        if (tryLoadFromModuleNativeDir()) return true

        // 2. 从模块 APK 解压
        if (tryExtractFromModuleApk()) return true

        // 3. 标准 loadLibrary
        if (tryLoadLibrary()) return true

        loaded = false
        Log.w("$TAG ★ 全部失败: $lastError")
        return false
    }

    // ==================== 方式 1 ====================

    private fun tryLoadFromModuleNativeDir(): Boolean = runCatching {
        val info = moduleInfo ?: queryModuleInfoViaPM() ?: run {
            lastError = "moduleInfo=null 且 PM 查询失败"
            return@runCatching false
        }
        if (moduleInfo == null) {
            moduleInfo = info
            Log.i("$TAG 通过 PM 补全模块信息: nativeDir=${info.nativeLibraryDir}")
        }
        val dir = info.nativeLibraryDir ?: run {
            lastError = "nativeLibraryDir=null"
            return@runCatching false
        }
        val f = File(dir, LIB)
        if (!f.exists()) {
            lastError = "nativeDir: $f 不存在"
            return@runCatching false
        }
        Log.i("$TAG 从模块 nativeDir 加载: ${f.absolutePath}")
        System.load(f.absolutePath)
        loaded = true
        Log.i("$TAG ★ 加载成功（nativeDir）")
        true
    }.getOrElse {
        lastError = "nativeDir: ${it.javaClass.simpleName}: ${it.message}"
        Log.w("$TAG nativeDir 失败: $lastError")
        false
    }

    // ==================== 方式 2 ====================

    private fun tryExtractFromModuleApk(): Boolean = runCatching {
        val info = moduleInfo ?: queryModuleInfoViaPM() ?: return@runCatching false
        val apk = info.sourceDir ?: return@runCatching false
        if (!File(apk).exists()) {
            lastError = "apk 不存在: $apk"
            return@runCatching false
        }

        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val entryName = "lib/$abi/$LIB"

        // 解压到模块自己的 cache 目录（宿主进程往模块目录写可能没权限，
        // 退回 /data/local/tmp）
        val outDirs = listOfNotNull(
            runCatching {
                Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null)
                    ?.let { (it as android.content.Context).codeCacheDir }
            }.getOrNull(),
            File("/data/local/tmp"),
        )

        var outFile: File? = null
        for (d in outDirs) {
            val target = File(d, "nekora_$LIB")
            val ok = runCatching {
                if (!target.exists() || target.length() == 0L) {
                    val jar = java.util.zip.ZipFile(apk)
                    val entry = jar.getEntry(entryName) ?: run {
                        jar.close()
                        return@runCatching false
                    }
                    jar.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { input.copyTo(it) }
                    }
                    jar.close()
                }
                target.setReadable(true, false)
                target.setExecutable(true, false)
                target.length() > 0
            }.getOrDefault(false)
            if (ok) { outFile = target; break }
        }

        val f = outFile ?: run {
            lastError = "extract: 无可写目录"
            return@runCatching false
        }

        Log.i("$TAG 解压到: ${f.absolutePath} (${f.length()} bytes)")
        System.load(f.absolutePath)
        loaded = true
        Log.i("$TAG ★ 加载成功（解压）")
        true
    }.getOrElse {
        lastError = "extract: ${it.javaClass.simpleName}: ${it.message}"
        Log.w("$TAG 解压失败: $lastError")
        false
    }

    // ==================== 方式 3 ====================

    private fun tryLoadLibrary(): Boolean = runCatching {
        System.loadLibrary("dexkit")
        loaded = true
        Log.i("$TAG ★ 加载成功（loadLibrary）")
        true
    }.getOrElse {
        lastError = "loadLibrary: ${it.message}"
        false
    }

    private fun FileOutputStream(f: File) = java.io.FileOutputStream(f)
}
