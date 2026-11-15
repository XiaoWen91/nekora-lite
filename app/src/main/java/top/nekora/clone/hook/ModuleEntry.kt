package top.nekora.clone.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import top.nekora.clone.dexkit.DexCache
import top.nekora.clone.dexkit.DexKitBootstrap
import top.nekora.clone.dexkit.DexKitResolver
import top.nekora.clone.hook.entry.CommentEntryHook
import top.nekora.clone.hook.entry.SettingsEntryHook
import top.nekora.clone.hook.entry.ShareEntryHook
import top.nekora.clone.hook.feature.BottomBarHook
import top.nekora.clone.hook.feature.CaptionOverlayHook
import top.nekora.clone.hook.feature.CommentBgHook
import top.nekora.clone.hook.feature.CommentFilterHook
import top.nekora.clone.hook.feature.CommentTimeHook
import top.nekora.clone.hook.feature.FeedAdFilterHook
import top.nekora.clone.hook.feature.InteractionHook
import top.nekora.clone.hook.feature.MarkAllReadHook
import top.nekora.clone.hook.feature.NoticeCleanHook
import top.nekora.clone.hook.feature.PopupBlockHook
import top.nekora.clone.hook.feature.RecallHook
import top.nekora.clone.hook.feature.SettingsSkinHook
import top.nekora.clone.hook.feature.SideMenuBgHook
import top.nekora.clone.hook.feature.SideMenuHook
import top.nekora.clone.hook.feature.TopBarHook
import top.nekora.clone.hook.feature.VideoFilterHook
import top.nekora.clone.state.ConfigStore
import top.nekora.clone.util.Log
import top.nekora.clone.util.SelfCheck

/**
 * 模块入口。
 *
 * 初始化顺序（按原版的逻辑）：
 *   1. 配置：走外部存储文件，两端共享
 *   2. DexKit：字节数组创建，带缓存
 *   3. 入口注入：资源 ID 定位
 *   4. 功能 hook：挂基类方法
 */
class ModuleEntry : XposedModule() {

    private var processName: String = ""
    private var installed = false

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        processName = param.processName
        if (param.isSystemServer || !isTargetProcess(processName)) {
            detach()
            return
        }
        Log.bind(this)
        Log.i("module-loaded api=$apiVersion framework=$frameworkName process=$processName")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val pkg = param.packageName
        if (!isTargetPackage(pkg)) return
        if (processName != pkg && !processName.startsWith("$pkg:")) return
        if (installed) return
        installed = true

        val cl = param.classLoader
        val isMainProcess = (processName == pkg)
        Log.i("========== package-ready pkg=$pkg process=$processName IS_MAIN=$isMainProcess ==========")

        // 绑定宿主 ClassLoader（供反射用）
        safe("BindLoader") { top.nekora.clone.hook.entry.ShareEntryHook.bindLoader(cl) }

        // 绑定模块自己的 ApplicationInfo —— 用于拿 nativeLibraryDir 加载 libdexkit.so
        safe("BindModuleInfo") {
            val info = runCatching { moduleApplicationInfo }.getOrNull()
            top.nekora.clone.dexkit.NativeLib.bindModule(info)
            Log.i("NativeLib bound=${info != null} nativeDir=${info?.nativeLibraryDir}")
        }

        // ---------- 1. 配置 ----------
        safe("Config") { ConfigStore.initInHost() }
        Log.i("config path = ${ConfigStore.path()}")

        // ---------- 2. DexKit 上下文（懒加载，进面板才扫） ----------
        safe("DexCache") { DexCache.initWith(this) }
        val apkPath = safe2("apkPath") { param.applicationInfo.sourceDir }
        val hostVer = safe2("hostVer") {
            runCatching {
                val ctx = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null) as? android.content.Context
                ctx?.packageManager?.getPackageInfo(pkg, 0)?.versionName
            }.getOrNull()
        } ?: "unknown"
        Log.i("apkPath=$apkPath hostVer=$hostVer")

        // 只记录上下文；扫描由 DexKitBootstrap 在进入快手时触发（与原版一致）
        DexKitResolver.prepare(cl, apkPath, pkg, hostVer)
        Log.i("dexkit context prepared, scan will be triggered on first activity")

        // 自检：探测所有依赖的类与资源名
        safe("SelfCheck") {
            val ctx = runCatching {
                Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null) as? android.content.Context
            }.getOrNull()
            SelfCheck.run(cl, ctx, processName)
        }

        // 主进程判定：进程名 == 包名（不含冒号）
        val isMain = (processName == pkg)
        Log.i("isMainProcess=$isMain process=$processName")

        // ---------- 3. UI 入口（只在主进程装，沙箱进程没有界面） ----------
        if (isMain) {
            // 进入快手即弹「是否查找 DexKit」——与原版 ensureShown 一致
            safe("DexKitBootstrap") {
                DexKitBootstrap.install(this) { ok ->
                    Log.i("dexkit bootstrap finished ok=$ok")
                }
            }
            safe("SettingsEntry") { SettingsEntryHook.install(this, cl, pkg) }
            safe("ShareEntry") { ShareEntryHook.install(this, cl, pkg) }
            safe("CommentEntry") { CommentEntryHook.install(this, cl, pkg) }
        } else {
            Log.i("非主进程，跳过 UI 入口注入")
        }

        // ---------- 4. 视频与浏览 ----------
        safe("VideoFilter") { VideoFilterHook.install(this, cl, pkg) }
        safe("FeedAdFilter") { FeedAdFilterHook.install(this, cl, pkg) }
        safe("PopupBlock") { PopupBlockHook.install(this, cl, pkg) }
        safe("CaptionOverlay") { CaptionOverlayHook.install(this, cl, pkg) }

        // ---------- 5. 交互（依赖 View，只在主进程） ----------
        if (isMain) safe("Interaction") { InteractionHook.install(this, pkg) }

        // ---------- 6. 界面（只在主进程） ----------
        if (isMain) {
            safe("TopBar") { TopBarHook.install(this, pkg) }
            safe("BottomBar") { BottomBarHook.install(this, cl, pkg) }
            safe("SideMenu") { SideMenuHook.install(this, pkg) }
            safe("SideMenuBg") { SideMenuBgHook.install(this, cl, pkg) }
            safe("SettingsSkin") { SettingsSkinHook.install(this, cl, pkg) }
            safe("NoticeClean") { NoticeCleanHook.install(this, pkg) }
            safe("MarkAllRead") { MarkAllReadHook.install(this, pkg) }
        }

        // ---------- 7. 消息与评论 ----------
        safe("Recall") { RecallHook.install(this, cl, pkg) }
        safe("CommentFilter") { CommentFilterHook.install(this, cl, pkg) }
        safe("CommentTime") { CommentTimeHook.install(this, cl, pkg) }
        if (isMain) safe("CommentBg") { CommentBgHook.install(this, cl, pkg) }

        Log.i("ALL hooks=${Log.counter} pkg=$pkg")

        Log.i("ready. DexKit 将在打开面板时按需查找")
    }

    private inline fun safe(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w("[$name] failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun <T> safe2(name: String, block: () -> T): T? =
        try {
            block()
        } catch (t: Throwable) {
            Log.w("[$name] failed: ${t.javaClass.simpleName}: ${t.message}")
            t.printStackTrace()
            null
        }

    private fun isTargetPackage(pkg: String) = TARGET_PACKAGES.contains(pkg)

    private fun isTargetProcess(name: String): Boolean {
        if (name.isBlank()) return false
        return TARGET_PACKAGES.any { name == it || name.startsWith("$it:") }
    }

    companion object {
        private val TARGET_PACKAGES = listOf("com.kuaishou.nebula", "com.smile.gifmaker")
    }
}
