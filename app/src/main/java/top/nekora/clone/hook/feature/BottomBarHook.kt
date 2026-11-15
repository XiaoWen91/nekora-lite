package top.nekora.clone.hook.feature

import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 底栏 hook：隐藏中间拍摄按钮 / 隐藏整个底栏 / 加号改双击。
 *
 * 目标类（快手 kcube 底栏）：
 *   com.kwai.kcube.ext.actionbar.bottom.BottomActionBar
 *   com.kwai.feature.api.feed.home.wrapper.kcube.widget.BottomBarShootView
 *   com.google.android.material.tabs.BottomActionBarTabLayout
 *
 * 思路与 Nekora 一致：hook BottomActionBar 的 attach 时机，
 * 在视图树上找到拍摄按钮，按配置设 GONE 或重排权重。
 */
object BottomBarHook {

    private const val TAG = "[BottomBar]"

    private const val CLS_ACTION_BAR = "com.kwai.kcube.ext.actionbar.bottom.BottomActionBar"
    private const val CLS_SHOOT_VIEW =
        "com.kwai.feature.api.feed.home.wrapper.kcube.widget.BottomBarShootView"
    private const val CLS_TAB_LAYOUT = "com.google.android.material.tabs.BottomActionBarTabLayout"

    private var lastClickAt = 0L

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        hookActionBarAttach(module, loader)
        if (Prefs.bottomBarShootDoubleTap) hookShootDoubleTap(module, loader)
        Log.hooked("$TAG install pkg=$pkg")
    }

    private fun hookActionBarAttach(module: XposedModule, loader: ClassLoader) {
        val cls = Reflect.findClass(loader, CLS_ACTION_BAR) ?: run {
            Log.w("$TAG $CLS_ACTION_BAR not found, skip")
            return
        }

        // attach 是 ViewGroup 里的虚方法，挂在 onAttachedToWindow 最稳
        val m = Reflect.findMethodInHierarchy(cls, "onAttachedToWindow")
            ?: Reflect.findMethodInHierarchy(cls, "attach")
            ?: run {
                Log.w("$TAG no attach method on $CLS_ACTION_BAR")
                return
            }

        module.hook(m).intercept { chain ->
            val result = chain.proceed()
            runCatching { applyOnBar(chain.thisObject as View) }
                .onFailure { Log.w("$TAG apply failed: ${it.message}") }
            result
        }
        Log.hooked("$TAG ActionBar.onAttachedToWindow")
    }

    private fun applyOnBar(bar: View) {
        if (!Prefs.bottomBarHidden && !Prefs.bottomBarHideShoot) return
        if (bar !is ViewGroup) return

        if (Prefs.bottomBarHidden) {
            bar.visibility = View.GONE
            Log.i("$TAG whole bottom bar hidden")
            return
        }

        // 只隐藏拍摄按钮，其余 Tab 平分
        val shoot = findShootView(bar)
        if (shoot != null) {
            shoot.visibility = View.GONE
            Log.i("$TAG shoot button hidden")
        } else {
            Log.w("$TAG shoot view not found in bar")
        }
    }

    private fun findShootView(root: ViewGroup): View? {
        val stack = ArrayDeque<View>()
        for (i in 0 until root.childCount) stack.addLast(root.getChildAt(i))

        while (stack.isNotEmpty()) {
            val v = stack.removeLast()
            val name = v.javaClass.name
            if (name == CLS_SHOOT_VIEW || name.endsWith("BottomBarShootView")) return v
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) stack.addLast(v.getChildAt(i))
            }
        }
        return null
    }

    /**
     * 加号改双击：hook View.performClick，
     * 命中拍摄按钮时要求 300ms 内第二次点击才放行。
     */
    private fun hookShootDoubleTap(module: XposedModule, loader: ClassLoader) {
        val performClick = runCatching {
            View::class.java.getDeclaredMethod("performClick").apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(performClick).intercept { chain ->
            val view = chain.thisObject as? View
            if (view == null || !isShootView(view)) {
                return@intercept chain.proceed()
            }
            val now = System.currentTimeMillis()
            if (now - lastClickAt <= 300L) {
                lastClickAt = 0L
                chain.proceed()
            } else {
                lastClickAt = now
                Log.i("$TAG shoot first tap swallowed, waiting for second")
                false
            }
        }
        Log.hooked("$TAG View.performClick double-tap")
    }

    private fun isShootView(v: View): Boolean {
        val n = v.javaClass.name
        if (n == CLS_SHOOT_VIEW || n.endsWith("BottomBarShootView")) return true
        // 混淆改名时的兜底：类名在父容器的 id 或 tag 里通常留有标记
        val id = runCatching { v.id }.getOrNull()
        if (id != null && id != View.NO_ID) {
            val entryName = runCatching {
                v.resources.getResourceEntryName(id)
            }.getOrNull()
            if (entryName != null && entryName.contains("shoot", ignoreCase = true)) return true
        }
        return false
    }
}
