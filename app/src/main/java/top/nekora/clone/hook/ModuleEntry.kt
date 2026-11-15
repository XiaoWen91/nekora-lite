package top.nekora.clone.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import top.nekora.clone.state.Prefs
import top.nekora.clone.hook.feature.BottomBarHook
import top.nekora.clone.hook.feature.CommentFilterHook
import top.nekora.clone.hook.feature.FeedAdFilterHook
import top.nekora.clone.hook.feature.RecallHook
import top.nekora.clone.hook.feature.SideMenuHook
import top.nekora.clone.hook.feature.TopBarHook
import top.nekora.clone.util.Log

/**
 * 模块入口。
 *
 * libxposed 102 通过 META-INF/xposed/java_init.list 声明本类，
 * 框架会在每个进程里实例化一次，然后依次回调 onModuleLoaded / onPackageReady。
 */
class ModuleEntry : XposedModule() {

    private var processName: String = ""

    /** 单进程只安装一次，防止热重载或多包回调重复挂 hook */
    private var installed = false

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        processName = param.processName

        // 系统进程无关，直接卸载，不占宿主内存
        if (param.isSystemServer || !isTargetProcess(processName)) {
            detach()
            return
        }

        Log.tag = "NekoraLite"
        Log.i(
            "module-loaded api=${apiVersion} framework=$frameworkName " +
                "frameworkVer=$frameworkVersion process=$processName"
        )
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val pkg = param.packageName
        if (!isTargetPackage(pkg)) return

        // 只在包自己的主进程 / 子进程里工作，别的 app 进程不碰
        if (processName != pkg && !processName.startsWith("$pkg:")) return

        if (installed) return
        installed = true

        Prefs.init(this, pkg)

        Log.i("package-ready pkg=$pkg process=$processName")

        // 每个功能独立 try/catch：单个失败不拖垮其它 hook
        safe("TopBar") { TopBarHook.install(this, param.classLoader, pkg) }
        safe("BottomBar") { BottomBarHook.install(this, param.classLoader, pkg) }
        safe("SideMenu") { SideMenuHook.install(this, param.classLoader, pkg) }
        safe("CommentFilter") { CommentFilterHook.install(this, param.classLoader, pkg) }
        safe("FeedAdFilter") { FeedAdFilterHook.install(this, param.classLoader, pkg) }
        safe("Recall") { RecallHook.install(this, param.classLoader, pkg) }

        Log.i("hooks installed=${Log.counter} pkg=$pkg")
    }

    private inline fun safe(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w("[$name] install failed: ${t.javaClass.simpleName}: ${t.message}")
        }
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
