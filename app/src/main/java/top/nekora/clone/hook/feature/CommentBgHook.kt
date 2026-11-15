package top.nekora.clone.hook.feature

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect
import top.nekora.clone.util.RoundRectBitmapDrawable
import top.nekora.clone.util.ViewFinder
import java.io.File

/**
 * 评论区背景美化（完整还原原版 ek.java [KwaiCommentBg]）。
 *
 * 原版关键点：
 *   目标容器（5 个资源名）：
 *     tabs_panel_container
 *     tabs_panel_comment_survey_container
 *     list_mask
 *     tabs_panel_operate_area
 *     tabs_panel_full
 *
 *   背景实现：new zq0(bitmap, 16dp, maskColor)  ← 圆角 16dp 的图片背景
 *   纯色模式：setBackgroundColor(0) 清空 + 设深色
 *
 *   StateListDrawable 需要递归每个 state 单独改，
 *   直接 setBackgroundColor 会被宿主的 state 切换覆盖。
 */
object CommentBgHook {

    private const val TAG = "[CommentBg]"

    /** 原版 f4719a：5 个目标容器资源名 */
    private val CONTAINER_RES = arrayOf(
        "tabs_panel_container",
        "tabs_panel_comment_survey_container",
        "list_mask",
        "tabs_panel_operate_area",
        "tabs_panel_full",
    )

    private val TARGET_CLASSES = listOf(
        "com.kwai.component.tabs.panel.widget.TabsPanelNestedParentRelativeLayout",
    )

    /** 深色纯色的默认值 */
    private const val DARK_BG = 0xFF1C1C1E.toInt()

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        var hooked = 0
        for (name in TARGET_CLASSES) {
            val cls = Reflect.findClass(loader, name) ?: continue
            val m = Reflect.findMethodInHierarchy(cls, "onFinishInflate")
                ?: Reflect.findMethodInHierarchy(cls, "onAttachedToWindow")
                ?: continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    val r = chain.proceed()
                    (chain.thisObject as? View)?.let { panel ->
                        runCatching { apply(panel) }
                            .onFailure { Log.w("$TAG apply: ${it.message}") }
                    }
                    r
                }
                hooked++
                Log.hooked("$TAG ${cls.simpleName}.${m.name}")
            }.onFailure { Log.w("$TAG hook: ${it.message}") }
        }
        if (hooked == 0) Log.w("$TAG 未找到评论面板类")
    }

    // ==================== 应用 ====================

    private fun apply(panel: View) {
        if (!Prefs.isEnabled(SettingsItem.COMMENT_BG)) {
            Log.i("$TAG 未启用")
            return
        }

        val mode = Prefs.getInt(Prefs.K_COMMENT_BG_MODE, 0)   // 0 深色纯色 1 图片
        val lightText = Prefs.getBool(Prefs.K_COMMENT_TEXT_LIGHT, true)
        val d = panel.resources.displayMetrics.density

        // 逐个容器处理
        var applied = 0
        for (resName in CONTAINER_RES) {
            val v = findByIdName(panel, resName) ?: continue
            when (mode) {
                1 -> {
                    val path = Prefs.getString(Prefs.K_COMMENT_BG_IMAGE)
                    val bmp = if (path.isNotEmpty() && File(path).exists()) {
                        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                    } else null
                    if (bmp != null) {
                        // 蒙版：浅色文字配深色蒙版，深色文字配浅色蒙版
                        val mask = if (lightText) 0x66000000 else 0x66FFFFFF
                        v.background = RoundRectBitmapDrawable(bmp, 16f * d, mask)
                        Log.i("$TAG [$resName] 图片背景 + 蒙版 ${if (lightText) "深" else "浅"}")
                    } else {
                        v.background = ColorDrawable(DARK_BG)
                        Log.w("$TAG [$resName] 图片缺失，退回深色")
                    }
                }
                0 -> {
                    // 原版：先清空 container 的背景色，再设深色
                    if (resName == "tabs_panel_container") {
                        runCatching { v.setBackgroundColor(0) }
                    }
                    replaceBackground(v, DARK_BG)
                    Log.i("$TAG [$resName] 深色纯色")
                }
            }
            applied++
        }

        if (applied == 0) {
            Log.w("$TAG 没有找到任何目标容器（资源名可能变了）")
            return
        }

        // 文字颜色
        val textColor = if (lightText) Color.WHITE else 0xFF1A1A1A.toInt()
        tintText(panel, textColor)
        Log.i("$TAG 完成：$applied 个容器，文字 ${if (lightText) "浅色" else "深色"}")
    }

    /**
     * 原版 m2654a：递归处理 StateListDrawable。
     * 直接 setBackgroundColor 会被宿主的 state 切换覆盖。
     */
    private fun replaceBackground(v: View, color: Int) {
        val bg = v.background
        when {
            bg is StateListDrawable -> {
                // 逐 state 改
                runCatching {
                    val count = bg.stateCount
                    for (i in 0 until count) {
                        val d = bg.getStateDrawable(i) ?: continue
                        if (d is ColorDrawable) {
                            (d.mutate() as? ColorDrawable)?.color = color
                        }
                    }
                }
                // 有些 state 改不到，再叠一层
                v.background = ColorDrawable(color)
            }
            bg is ColorDrawable -> {
                if (Color.alpha(bg.color) == 255) {
                    (bg.mutate() as? ColorDrawable)?.color = color
                } else {
                    v.background = ColorDrawable(color)
                }
            }
            else -> v.background = ColorDrawable(color)
        }
    }

    /** 调文字颜色，并给子容器背景加透明度让背景透出来 */
    private fun tintText(root: View, color: Int) {
        ViewFinder.walk(root, maxDepth = 10) { v, _ ->
            if (v is TextView) {
                runCatching { v.setTextColor(color) }
            }
            if (v is ViewGroup && v !== root) {
                val b = v.background
                if (b is ColorDrawable && Color.alpha(b.color) == 255) {
                    runCatching { b.alpha = 40 }
                }
            }
        }
    }

    private fun findByIdName(root: View, resName: String): View? {
        val ctx = runCatching { root.context }.getOrNull() ?: return null
        val id = runCatching {
            ctx.resources.getIdentifier(resName, "id", ctx.packageName)
        }.getOrDefault(0)
        if (id == 0) return null
        return runCatching { root.findViewById<View>(id) }.getOrNull()
    }
}
