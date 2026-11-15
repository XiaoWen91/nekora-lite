package top.nekora.clone.util

import android.view.View
import android.view.ViewGroup

/**
 * 彻底隐藏一个 View。
 *
 * 完整还原原版 in.java m3315a 的 11 步操作：
 *   只设 GONE 有些版本仍会占位，必须把所有尺寸、透明度、交互全部清零。
 */
object ViewHider {

    private val handled = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<View, Boolean>()
    )

    fun hideHard(view: View) {
        if (!handled.add(view)) return
        runCatching {
            // 1. 文字清零（TextView 专用）
            if (view is android.widget.TextView) {
                view.text = ""
                view.textSize = 0f
                view.includeFontPadding = false
            }
            // 2-4. 可见性 + 透明度 + 交互
            view.visibility = View.GONE
            view.alpha = 0f
            view.isClickable = false
            view.isEnabled = false
            // 5-7. 内边距与最小尺寸
            view.setPadding(0, 0, 0, 0)
            view.minimumWidth = 0
            view.minimumHeight = 0
            // 8-9. 布局尺寸归零
            view.layoutParams?.let { lp ->
                lp.width = 0
                lp.height = 0
                view.layoutParams = lp
            }
            // 10-11. 请求父容器重排
            (view.parent as? ViewGroup)?.let {
                it.requestLayout()
                it.invalidate()
            }
            handled.remove(view)
        }.onFailure {
            handled.remove(view)
        }
    }

    /** 轻量隐藏：只设 GONE（用于临时隐藏，不破坏布局） */
    fun hideSoft(view: View) {
        runCatching { view.visibility = View.GONE }
    }

    /** 从父容器彻底移除 */
    fun removeFromParent(view: View) {
        runCatching { (view.parent as? ViewGroup)?.removeView(view) }
    }
}
