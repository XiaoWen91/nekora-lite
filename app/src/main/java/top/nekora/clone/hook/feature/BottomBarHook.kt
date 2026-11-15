package top.nekora.clone.hook.feature

import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect
import top.nekora.clone.util.ViewFinder
import top.nekora.clone.util.ViewHider

/**
 * 底栏与手势条（对应原版 mh.java [KwaiBarAction] + xl.java [KwaiGestureBar]）。
 *
 * 原版关键点：**hook 底栏类的构造器**，并且 hook
 * `BottomActionBarTabLayout.setHasShootButton(boolean)` 这个专门方法。
 *
 * 目标类：
 *   com.kwai.kcube.ext.actionbar.bottom.BottomActionBar
 *   com.kwai.feature.api.feed.home.wrapper.kcube.widget.BottomBarShootView
 *   com.google.android.material.tabs.BottomActionBarTabLayout
 */
object BottomBarHook {

    private const val TAG = "[BottomBar]"

    private const val CLS_ACTION_BAR =
        "com.kwai.kcube.ext.actionbar.bottom.BottomActionBar"
    private const val CLS_SHOOT =
        "com.kwai.feature.api.feed.home.wrapper.kcube.widget.BottomBarShootView"
    private const val CLS_TAB_LAYOUT =
        "com.google.android.material.tabs.BottomActionBarTabLayout"

    private val SHOOT_RES = arrayOf("shoot", "bottom_shoot", "shoot_button")
    private val GESTURE_RES = arrayOf("navigationbar", "navigation_bar", "gesture")

    private var lastShootTap = 0L
    private const val DOUBLE_MS = 300L

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        hookActionBarCtor(module, loader)
        hookShootCtor(module, loader)
        hookSetHasShootButton(module, loader)
        hookViewAttach(module)
        hookPerformClick(module)
        Log.hooked("$TAG install pkg=$pkg")
    }

    /** hook 底栏构造器：底栏一被创建就处理 */
    private fun hookActionBarCtor(module: XposedModule, loader: ClassLoader) {
        val cls = Reflect.findClass(loader, CLS_ACTION_BAR) ?: run {
            Log.w("$TAG $CLS_ACTION_BAR not found")
            return
        }
        var count = 0
        for (ctor in cls.declaredConstructors) {
            runCatching {
                ctor.isAccessible = true
                module.hook(ctor).intercept { chain ->
                    val r = chain.proceed()
                    (chain.thisObject as? View)?.let { applyBar(it) }
                    r
                }
                count++
            }.onFailure { Log.w("$TAG ctor hook failed: ${it.message}") }
        }
        if (count > 0) Log.i("$TAG hooked BottomActionBar x$count ctor")
    }

    private fun hookShootCtor(module: XposedModule, loader: ClassLoader) {
        val cls = Reflect.findClass(loader, CLS_SHOOT) ?: return
        var count = 0
        for (ctor in cls.declaredConstructors) {
            runCatching {
                ctor.isAccessible = true
                module.hook(ctor).intercept { chain ->
                    val r = chain.proceed()
                    val v = chain.thisObject as? View
                    if (v != null && Prefs.isEnabled(SettingsItem.BOTTOM_BAR) &&
                        Prefs.getBool(Prefs.K_BOTTOM_SHOOT_HIDDEN)
                    ) {
                        ViewHider.hideHard(v)
                        Log.i("$TAG shoot hidden (ctor)")
                    }
                    r
                }
                count++
            }.onFailure { Log.w("$TAG shoot ctor failed: ${it.message}") }
        }
        if (count > 0) Log.i("$TAG hooked BottomBarShootView x$count ctor")
    }

    /**
     * hook setHasShootButton(boolean)：
     * 这是原版最关键的一招——让底栏自己认为「没有拍摄按钮」，
     * 从而四个 Tab 自动平分，不需要手动改布局。
     */
    private fun hookSetHasShootButton(module: XposedModule, loader: ClassLoader) {
        val cls = Reflect.findClass(loader, CLS_TAB_LAYOUT) ?: return
        val m = runCatching {
            cls.getDeclaredMethod("setHasShootButton", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
        }.getOrNull() ?: run {
            // 退路：按签名找
            cls.declaredMethods.firstOrNull {
                it.returnType == Void.TYPE &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true }
        } ?: run {
            Log.w("$TAG setHasShootButton not found")
            return
        }

        module.hook(m).intercept { chain ->
            if (Prefs.isEnabled(SettingsItem.BOTTOM_BAR) &&
                Prefs.getBool(Prefs.K_BOTTOM_SHOOT_HIDDEN)
            ) {
                Log.i("$TAG setHasShootButton -> false")
                return@intercept chain.proceed(arrayOf<Any?>(false))
            }
            chain.proceed()
        }
        Log.i("$TAG hooked setHasShootButton")
    }

    /** 兜底：靠 onAttachedToWindow 处理 */
    private fun hookViewAttach(module: XposedModule) {
        val m = runCatching {
            View::class.java.getDeclaredMethod("onAttachedToWindow").apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val r = chain.proceed()
            (chain.thisObject as? View)?.let { v ->
                val cn = v.javaClass.name
                if (cn.contains("BottomActionBar") || cn.contains("BottomBarShootView")) {
                    runCatching { applyBar(v) }
                }
            }
            r
        }
        Log.i("$TAG hooked View.onAttachedToWindow")
    }

    private fun applyBar(bar: View) {
        if (!Prefs.isEnabled(SettingsItem.BOTTOM_BAR)) return

        // 隐藏整个底栏
        if (Prefs.getBool(Prefs.K_BOTTOM_BAR_ALL_HIDDEN)) {
            ViewHider.hideHard(bar)
            Log.i("$TAG whole bottom bar hidden")
            return
        }

        // 隐藏拍摄按钮
        if (Prefs.getBool(Prefs.K_BOTTOM_SHOOT_HIDDEN)) {
            val shoot = ViewFinder.findByName(bar, *SHOOT_RES)
                ?: findByClassHint(bar, "ShootView")
            shoot?.let {
                ViewHider.hideHard(it)
                Log.i("$TAG shoot hidden (attach)")
            }
        }

        // 隐藏手势条
        if (Prefs.getBool(Prefs.K_GESTURE_BAR_HIDDEN)) {
            val g = ViewFinder.findByName(bar, *GESTURE_RES)
            g?.let {
                ViewHider.hideHard(it)
                Log.i("$TAG gesture bar hidden")
            }
        }
    }

    private fun findByClassHint(root: View, hint: String): View? {
        var found: View? = null
        ViewFinder.walk(root) { v, _ ->
            if (found == null && v.javaClass.name.contains(hint, true)) found = v
        }
        return found
    }

    /** 加号改双击 */
    private fun hookPerformClick(module: XposedModule) {
        val m = runCatching {
            View::class.java.getDeclaredMethod("performClick").apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val v = chain.thisObject as? View ?: return@intercept chain.proceed()

            if (!Prefs.isEnabled(SettingsItem.BOTTOM_BAR) ||
                !Prefs.getBool(Prefs.K_BOTTOM_SHOOT_DOUBLE)
            ) return@intercept chain.proceed()

            val isShoot = ViewFinder.matchesName(v, *SHOOT_RES) ||
                v.javaClass.name.contains("ShootView", true)
            if (!isShoot) return@intercept chain.proceed()

            val now = System.currentTimeMillis()
            if (now - lastShootTap <= DOUBLE_MS) {
                lastShootTap = 0L
                Log.i("$TAG shoot double-tap accepted")
                chain.proceed()
            } else {
                lastShootTap = now
                Log.i("$TAG shoot first tap swallowed")
                false
            }
        }
        Log.i("$TAG hooked View.performClick")
    }
}
