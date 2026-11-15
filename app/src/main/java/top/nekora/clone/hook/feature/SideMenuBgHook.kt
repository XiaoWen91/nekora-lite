package top.nekora.clone.hook.feature

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect
import top.nekora.clone.util.ViewFinder
import java.io.File

/**
 * 侧边栏背景美化（完整还原原版 zw.java [KwaiSideMenuBg]）。
 *
 * 原版关键点：
 *   容器类：androidx.slidingpanelayout.widget.KwaiSlidingPaneLayout
 *   背景 drawable 类型：TKViewBackgroundDrawable
 *   hook：容器的**构造器**（hookAllConstructors）
 *
 * 状态模型 r01：
 *   enabled=false, mode=0, color=0xFFF7F7FA, bgAlpha=100, cardAlpha=100, textLight=false
 */
object SideMenuBgHook {

    private const val TAG = "[SideMenuBg]"

    private const val CLS_PANE = "androidx.slidingpanelayout.widget.KwaiSlidingPaneLayout"
    private const val CLS_BG = "TKViewBackgroundDrawable"

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        val cls = Reflect.findClass(loader, CLS_PANE)
        if (cls == null) {
            Log.w("$TAG $CLS_PANE 不存在，改用通用兜底")
            hookGeneric(module)
            return
        }

        var n = 0
        for (ctor in cls.declaredConstructors) {
            runCatching {
                ctor.isAccessible = true
                module.hook(ctor).intercept { chain ->
                    val r = chain.proceed()
                    (chain.thisObject as? ViewGroup)?.let { g ->
                        runCatching { apply(g) }
                            .onFailure { Log.w("$TAG apply: ${it.message}") }
                    }
                    r
                }
                n++
            }.onFailure { Log.w("$TAG ctor hook 失败: ${it.message}") }
        }
        if (n > 0) Log.hooked("$TAG KwaiSlidingPaneLayout x$n ctor")
    }

    private fun hookGeneric(module: XposedModule) {
        val m = runCatching {
            View::class.java.getDeclaredMethod("onAttachedToWindow").apply { isAccessible = true }
        }.getOrNull() ?: return
        module.hook(m).intercept { chain ->
            val r = chain.proceed()
            val v = chain.thisObject as? View
            if (v != null && (v.javaClass.name.contains("SlidingPane", true) ||
                    v.javaClass.name.contains("SideMenu", true))
            ) {
                runCatching { apply(v) }
            }
            r
        }
        Log.hooked("$TAG View.onAttachedToWindow (generic)")
    }

    // ==================== 应用背景 ====================

    private fun apply(root: View) {
        if (!Prefs.isEnabled(SettingsItem.SIDE_MENU)) {
            // 侧边栏背景用独立开关
        }
        val bgEnabled = Prefs.getBool("side_menu_bg.enabled")
        if (!bgEnabled) return

        val mode = Prefs.getInt(Prefs.K_SIDE_BG_MODE, 0)          // 0 纯色 1 图片
        val bgAlpha = Prefs.getInt(Prefs.K_SIDE_BG_ALPHA, 100).coerceIn(0, 100)
        val cardAlpha = Prefs.getInt(Prefs.K_SIDE_CARD_ALPHA, 100).coerceIn(0, 100)
        val color = Prefs.getInt(Prefs.K_SIDE_BG_COLOR, 0xFFF7F7FA.toInt())

        // 图片模式
        if (mode == 1) {
            val path = Prefs.getString(Prefs.K_SIDE_BG_IMAGE)
            if (path.isNotEmpty() && File(path).exists()) {
                val d = runCatching { Drawable.createFromPath(path) }.getOrNull()
                if (d != null) {
                    runCatching { d.mutate().alpha = bgAlpha * 255 / 100 }
                    root.background = d
                    tintCards(root, cardAlpha)
                    Log.i("$TAG 图片背景 alpha=$bgAlpha% card=$cardAlpha%")
                    return
                }
            }
            Log.w("$TAG 图片缺失，退回纯色")
        }

        // 纯色模式
        val c = Color.argb(bgAlpha * 255 / 100, Color.red(color), Color.green(color), Color.blue(color))
        root.background = ColorDrawable(c)
        tintCards(root, cardAlpha)
        Log.i("$TAG 纯色背景 #%08X alpha=$bgAlpha%% card=$cardAlpha%%".format(c))
    }

    /**
     * 原版 m5970a：只处理 TKViewBackgroundDrawable 类型的背景。
     * 动别的类型会把界面弄花。
     */
    private fun tintCards(root: View, cardAlpha: Int) {
        if (cardAlpha >= 100) return
        ViewFinder.walk(root, maxDepth = 8) { v, depth ->
            if (depth == 0 || v === root) return@walk
            if (v !is ViewGroup) return@walk
            val bg = v.background ?: return@walk
            if (bg.javaClass.simpleName.contains(CLS_BG)) {
                runCatching { bg.mutate().alpha = cardAlpha * 255 / 100 }
            }
        }
    }
}
