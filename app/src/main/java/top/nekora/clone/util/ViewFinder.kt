package top.nekora.clone.util

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * 资源 ID / 资源名定位工具。
 *
 * **这是原版能在混淆环境里工作的核心技巧。**
 * 快手的类名每次发版都变，但 layout 里的 android:id 资源名基本不变，
 * 所以用 getIdentifier(name, "id", pkg) 拿 id，比按类名找稳得多。
 *
 * 原版用到的资源名（从 in.java / mp.java / jk.java / ek.java 提取）：
 *   顶栏容器: pagerslidingtabstrip / kcubetabactionbar / kcubetabstrip
 *             hometop / topbar / toptab / titletab
 *             slidingtabindicator / tablayout / tabstrip
 *   点赞:     like_button / like_element_click_layout / like
 *   收藏:     collect_button / collect
 *   评论:     tabs_panel_close / tabs_panel_operate_area / tabs_panel_container
 *             tabs_panel_comment_survey_container / list_mask / tabs_panel_full
 *   消息:     contact_layout / close_pymk / follower_layout
 *             pymkFooterLayout / message_root_layout / recycler_view
 *   底栏:     bottomactionbar / bottomnavigation / bottomtab
 */
object ViewFinder {

    private val idCache = HashMap<String, Int>()

    /** 按资源名取 id，取不到返回 0 */
    fun id(ctx: Context, resName: String, pkg: String? = null): Int {
        val host = pkg ?: ctx.packageName
        val key = "$host/$resName"
        idCache[key]?.let { return it }
        val id = runCatching {
            ctx.resources.getIdentifier(resName, "id", host)
        }.getOrDefault(0)
        idCache[key] = id
        return id
    }

    /** 在 View 树里按资源名找第一个匹配的 View */
    fun findByName(root: View, vararg resNames: String): View? {
        val ctx = root.context ?: return null
        val pkg = ctx.packageName
        val targets = resNames.mapNotNull { id(ctx, it, pkg).takeIf { v -> v != 0 } }.toSet()
        if (targets.isEmpty()) return null

        val stack = ArrayDeque<View>()
        stack.addLast(root)
        var guard = 0
        while (stack.isNotEmpty() && guard++ < 1000) {
            val v = stack.removeLast()
            if (v.id in targets) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) stack.addLast(v.getChildAt(i))
        }
        return null
    }

    /** 判断 View 的 id 是否等于给定资源名之一 */
    fun matchesName(v: View, vararg resNames: String): Boolean {
        if (v.id == View.NO_ID) return false
        val ctx = runCatching { v.context }.getOrNull() ?: return false
        val pkg = ctx.packageName
        return resNames.any { name ->
            val target = id(ctx, name, pkg)
            target != 0 && v.id == target
        }
    }

    /** 判断 View 的 id 资源名是否包含关键词（用于模糊匹配） */
    fun idNameContains(v: View, vararg keywords: String): Boolean {
        if (v.id == View.NO_ID) return false
        val name = runCatching {
            v.resources.getResourceEntryName(v.id)
        }.getOrNull()?.lowercase() ?: return false
        return keywords.any { name.contains(it.lowercase()) }
    }

    /** 转发到 ViewHider（原版 11 步彻底隐藏） */
    fun hideHard(v: View) = ViewHider.hideHard(v)

    /** 旧实现，已弃用 */
    fun hideHardOld(v: View) {
        runCatching {
            v.visibility = View.GONE
            v.layoutParams?.let { lp ->
                lp.height = 0
                v.layoutParams = lp
            }
            (v.parent as? ViewGroup)?.requestLayout()
        }
    }

    /** 递归遍历（带深度与次数保护） */
    fun walk(root: View, maxDepth: Int = 12, block: (View, Int) -> Unit) {
        fun rec(v: View, d: Int) {
            if (d > maxDepth) return
            block(v, d)
            if (v is ViewGroup) for (i in 0 until v.childCount) rec(v.getChildAt(i), d + 1)
        }
        runCatching { rec(root, 0) }
    }

    /** 收集一个 View 子树里的全部文本 */
    fun collectText(root: View, maxLen: Int = 80): String {
        val sb = StringBuilder()
        walk(root) { v, _ ->
            if (sb.length < maxLen && v is android.widget.TextView) {
                v.text?.let { sb.append(it) }
            }
        }
        return sb.toString()
    }
}
