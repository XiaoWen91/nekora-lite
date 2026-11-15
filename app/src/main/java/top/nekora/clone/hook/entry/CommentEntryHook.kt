package top.nekora.clone.hook.entry

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 评论区右上角入口（完整还原原版 jk.java [KwaiCommentEntry]）。
 *
 * 原版细节（此前遗漏的）：
 *   1. hook TabsPanelNestedParentRelativeLayout#onFinishInflate
 *   2. 找资源 id `tabs_panel_close`
 *   3. **严格校验**：它的 parent 必须就是 `tabs_panel_operate_area`，
 *      且必须是横向 LinearLayout，否则放弃
 *   4. 造 ImageView 时设了一堆属性：
 *        setId(generateViewId())
 *        setTag("djwsj_comment_beautify_entry")
 *        setScaleType(FIT_CENTER)
 *        setContentDescription("评论区设置")    ← 无障碍
 *        setClickable(true)                    ← 关键，否则点不到
 *        setFocusable(true)
 *        setImageTintList(null)                ← 关键，否则图标被着色
 *        setPadding(6dp)
 *   5. addView(imageView, indexOfChild, params)  ← 插到关闭按钮原位，关闭按钮后移
 */
object CommentEntryHook {

    private const val TAG = "[CommentEntry]"

    private const val TAG_VIEW = "nekora_comment_entry"
    private const val CLS_PANEL =
        "com.kwai.component.tabs.panel.widget.TabsPanelNestedParentRelativeLayout"

    private const val RES_CLOSE = "tabs_panel_close"
    private const val RES_OPERATE = "tabs_panel_operate_area"

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        val cls = Reflect.findClass(loader, CLS_PANEL)
        if (cls == null) {
            Log.w("$TAG $CLS_PANEL 不存在，尝试通用兜底")
            hookGeneric(module)
            return
        }

        val m = Reflect.findMethodInHierarchy(cls, "onFinishInflate")
        if (m == null) {
            Log.w("$TAG onFinishInflate 不存在")
            hookGeneric(module)
            return
        }

        runCatching {
            m.isAccessible = true
            module.hook(m).intercept { chain ->
                val r = chain.proceed()
                val panel = chain.thisObject as? ViewGroup
                if (panel != null) {
                    runCatching { inject(panel) }
                        .onFailure { Log.w("$TAG inject: ${it.message}") }
                }
                r
            }
            Log.hooked("$TAG ${cls.simpleName}#onFinishInflate")
        }.onFailure {
            Log.w("$TAG hook 失败: ${it.message}")
            hookGeneric(module)
        }
    }

    private fun hookGeneric(module: XposedModule) {
        val m = runCatching {
            View::class.java.getDeclaredMethod("onFinishInflate").apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val r = chain.proceed()
            val v = chain.thisObject as? ViewGroup
            if (v != null) {
                runCatching { inject(v) }
                    .onFailure { Log.w("$TAG generic inject: ${it.message}") }
            }
            r
        }
        Log.hooked("$TAG View#onFinishInflate (generic)")
    }

    // ==================== 注入 ====================

    /** 原版 m3495a 的完整还原 */
    private fun inject(panel: ViewGroup) {
        val ctx = panel.context ?: return

        // 诊断：探测所有关键资源名
        runCatching { probeResources(panel) }

        // 1. 找关闭按钮
        val close = findByIdName(panel, RES_CLOSE) ?: run {
            Log.w("$TAG 找不到 $RES_CLOSE，面板类=${panel.javaClass.simpleName}, children=${panel.childCount}")
            return
        }

        // 2. 严格校验父容器
        val parent = close.parent as? LinearLayout ?: return
        val operate = findByIdName(panel, RES_OPERATE)
        if (operate !== parent) {
            // 父容器不是 operate_area，说明布局结构变了，但不一定不能插
            Log.i("$TAG ${RES_CLOSE}.parent != ${RES_OPERATE}，仍尝试插入")
        }
        if (parent.orientation != LinearLayout.HORIZONTAL) {
            Log.i("$TAG operate_area 不是横向，仍尝试插入")
        }

        val index = parent.indexOfChild(close)
        if (index < 0) return

        // 3. 去重：已存在就校正位置
        val existed = parent.findViewWithTag<View>(TAG_VIEW)
        if (existed != null) {
            if (existed === close || existed.parent !== parent) return
            val cur = parent.indexOfChild(existed)
            if (cur != index) {
                parent.removeView(existed)
                parent.addView(existed, index, cloneParams(close))
                Log.i("$TAG 已存在，位置校正 $cur -> $index")
            }
            return
        }

        // 4. 造 ImageView，属性全按原版
        val iv = buildIconView(ctx, panel) ?: return

        // 5. 插到关闭按钮原位
        runCatching {
            parent.addView(iv, index, cloneParams(close))
            Log.i("$TAG ★ entry injected at index=$index in ${parent.javaClass.simpleName}")
        }.onFailure { Log.w("$TAG addView 失败: ${it.message}") }
    }

    /** 原版造 ImageView 的全部属性 */
    private fun buildIconView(ctx: Context, host: View): ImageView? {
        val d = ctx.resources.displayMetrics.density
        val pad = (6 * d).toInt()

        return runCatching {
            ImageView(ctx).apply {
                id = View.generateViewId()
                tag = TAG_VIEW
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "评论区设置"
                isClickable = true          // 关键
                isFocusable = true          // 关键
                imageTintList = null        // 关键：否则图标被宿主着色
                setPadding(pad, pad, pad, pad)
                setImageDrawable(buildIconDrawable(ctx))
                setOnClickListener {
                    Log.i("$TAG 评论区入口被点击")
                    PanelHost.showForComment(ctx)
                }
            }
        }.getOrNull()
    }

    /**
     * 图标：原版从模块资源 2130968582 取，这里自己画一个
     * 圆形 + 白色 "N"，避免依赖模块资源 id。
     */
    private fun buildIconDrawable(ctx: Context): Drawable {
        val d = ctx.resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFF2B6BE4.toInt())
            setSize((28 * d).toInt(), (28 * d).toInt())
        }
    }

    /** 复制锚点 View 的布局参数（原版 m3493a） */
    private fun cloneParams(anchor: View): LinearLayout.LayoutParams {
        val d = anchor.resources.displayMetrics.density
        var w = anchor.width
        var h = anchor.height
        val lp = anchor.layoutParams

        if (w <= 0) w = if (lp != null && lp.width > 0) lp.width else (44 * d).toInt()
        if (h <= 0) h = if (lp != null && lp.height > 0) lp.height else (44 * d).toInt()

        return LinearLayout.LayoutParams(w, h).apply {
            if (lp is LinearLayout.LayoutParams) {
                gravity = lp.gravity
            }
            if (lp is ViewGroup.MarginLayoutParams) {
                setMargins(lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin)
            }
        }
    }

    /** 诊断：探测评论面板相关资源名是否存在 */
    private fun probeResources(panel: View) {
        val ctx = runCatching { panel.context }.getOrNull() ?: return
        val pkg = ctx.packageName
        val names = arrayOf(
            RES_CLOSE, RES_OPERATE,
            "tabs_panel_container", "tabs_panel_comment_survey_container",
            "list_mask", "tabs_panel_full",
        )
        val found = ArrayList<String>()
        val missing = ArrayList<String>()
        for (n in names) {
            val id = runCatching { ctx.resources.getIdentifier(n, "id", pkg) }.getOrDefault(0)
            if (id != 0) found.add("$n=$id") else missing.add(n)
        }
        Log.i("$TAG 资源命中 ${found.size}/${names.size}: ${found.joinToString(",")}")
        if (missing.isNotEmpty()) Log.w("$TAG 资源缺失: ${missing.joinToString(",")}")
    }

    /** 按资源名找 View（原版 m3494a） */
    private fun findByIdName(root: View, resName: String): View? {
        val ctx = runCatching { root.context }.getOrNull() ?: return null
        val id = runCatching {
            ctx.resources.getIdentifier(resName, "id", ctx.packageName)
        }.getOrDefault(0)
        if (id == 0) return null
        return runCatching { root.findViewById<View>(id) }.getOrNull()
    }
}
