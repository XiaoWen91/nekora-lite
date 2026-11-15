package top.nekora.clone.hook.feature

import android.app.Application
import android.content.Context
import android.view.View
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.ViewFinder
import top.nekora.clone.util.ViewHider

/**
 * 顶栏频道隐藏（对应原版 in.java [KSS-HomeTopTabHide]）。
 *
 * 原版关键点：**用资源名识别顶栏容器，不靠类名**。
 * 顶栏容器的资源名（原版 in.java 里逐个 tryMatch）：
 *   pagerslidingtabstrip / kcubetabactionbar / kcubetabstrip
 *   hometop / topbar / toptab / titletab
 * 指标条资源名：
 *   slidingtabindicator / tablayout / tabstrip
 *
 * hook 点（全部挂在基类上，一个 hook 覆盖所有宿主子类）：
 *   Application.attach        → 拿 Context
 *   View.onAttachedToWindow   → 视图挂载时机
 *   View.setVisibility        → 跟踪可见性（原版也 hook 了这个）
 *   TextView.setText          → 拿到频道名文本
 */
object TopBarHook {

    private const val TAG = "[TopBar]"

    /** 顶栏容器资源名（原版逐个匹配的） */
    private val CONTAINER_RES = arrayOf(
        "pagerslidingtabstrip", "kcubetabactionbar", "kcubetabstrip",
        "hometop", "topbar", "toptab", "titletab",
    )

    /** 指标条资源名 */
    private val INDICATOR_RES = arrayOf(
        "slidingtabindicator", "tablayout", "tabstrip",
    )

    /** 类名辅助判断（资源名都不中时用） */
    private val CONTAINER_CLASS_HINTS = listOf(
        "TabAutoSizeLayout", "KwaiImageView", "TabStrip", "TopTab",
    )

    private var appCtx: Context? = null

    fun install(module: XposedModule, pkg: String) {
        hookApplicationAttach(module)
        hookViewAttach(module)
        hookSetText(module)
        hookSetVisibility(module)

        // 首次有 Context 时探测顶栏资源名
        Thread {
            Thread.sleep(3000L)
            appCtx?.let { probeResources(it) }
        }.apply { isDaemon = true }.start()

        Log.hooked("$TAG install pkg=$pkg")
    }

    private fun hookApplicationAttach(module: XposedModule) {
        val m = runCatching {
            Application::class.java
                .getDeclaredMethod("attach", Context::class.java)
                .apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val r = chain.proceed()
            (chain.getArg(0) as? Context)?.let { appCtx = it }
            r
        }
        Log.i("$TAG hooked Application.attach")
    }

    private fun hookViewAttach(module: XposedModule) {
        val m = runCatching {
            View::class.java.getDeclaredMethod("onAttachedToWindow").apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val r = chain.proceed()
            val v = chain.thisObject as? View
            if (v != null) {
                runCatching { onViewAttached(v) }
                    .onFailure { Log.w("$TAG attach failed: ${it.message}") }
            }
            r
        }
        Log.i("$TAG hooked View.onAttachedToWindow")
    }

    private fun hookSetText(module: XposedModule) {
        val m = runCatching {
            TextView::class.java
                .getDeclaredMethod("setText", CharSequence::class.java)
                .apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            if (!Prefs.isEnabled(SettingsItem.TOP_BAR)) return@intercept chain.proceed()

            val tv = chain.thisObject as? TextView
            val text = (chain.getArg(0) as? CharSequence)?.toString()?.trim() ?: ""
            if (tv != null && text.isNotEmpty()) {
                val hidden = Prefs.getSet(Prefs.K_TOP_BAR_TABS)
                if (hidden.any { it.equals(text, true) }) {
                    val holder = findTabHolder(tv)
                    if (holder != null) {
                        Log.i("$TAG hide tab '$text' (by setText)")
                        ViewHider.hideHard(holder)
                    }
                }
            }
            chain.proceed()
        }
        Log.i("$TAG hooked TextView.setText")
    }

    /** 原版也 hook 了 setVisibility，用于跟踪顶栏容器状态 */
    private fun hookSetVisibility(module: XposedModule) {
        val m = runCatching {
            View::class.java
                .getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val v = chain.thisObject as? View
            val vis = chain.getArg(0) as? Int ?: 0
            if (v != null && vis != View.VISIBLE && Prefs.isEnabled(SettingsItem.TOP_BAR)) {
                // 只做记录，不干预宿主的隐藏行为
                if (isTopBarContainer(v)) {
                    Log.i("$TAG topbar container visibility=$vis (${v.javaClass.simpleName})")
                }
            }
            chain.proceed()
        }
        Log.i("$TAG hooked View.setVisibility")
    }

    /** 诊断：探测顶栏相关资源名 */
    private fun probeResources(ctx: Context) {
        val pkg = ctx.packageName
        val names = CONTAINER_RES + INDICATOR_RES +
            arrayOf("bottomactionbar", "bottomtab", "bottomnavigation", "navigationbar")
        val found = ArrayList<String>()
        val missing = ArrayList<String>()
        for (n in names) {
            val id = runCatching { ctx.resources.getIdentifier(n, "id", pkg) }.getOrDefault(0)
            if (id != 0) found.add("$n=$id") else missing.add(n)
        }
        Log.i("$TAG 资源命中 ${found.size}/${names.size}")
        if (found.isNotEmpty()) Log.i("$TAG 明细: ${found.joinToString(",")}")
        if (missing.isNotEmpty()) Log.w("$TAG 缺失: ${missing.joinToString(",")}")
    }

    // ==================== 判断 ====================

    private fun onViewAttached(v: View) {
        if (!Prefs.isEnabled(SettingsItem.TOP_BAR)) return
        val hidden = Prefs.getSet(Prefs.K_TOP_BAR_TABS)
        if (hidden.isEmpty()) return
        if (!isTopBarContainer(v)) return

        // 顶栏容器挂载后，遍历它的子项按文本隐藏
        Log.i("$TAG topbar container attached: ${v.javaClass.simpleName}")
        ViewFinder.walk(v, maxDepth = 4) { child, depth ->
            if (depth == 0) return@walk
            val label = (child as? TextView)?.text?.toString()?.trim() ?: return@walk
            if (label.isEmpty()) return@walk
            if (hidden.any { it.equals(label, true) }) {
                val holder = findTabHolder(child) ?: child
                Log.i("$TAG hide tab '$label' (by attach)")
                ViewHider.hideHard(holder)
            }
        }
    }

    /** 是否顶栏容器：先查资源名，再退回类名 */
    private fun isTopBarContainer(v: View): Boolean {
        if (ViewFinder.idNameContains(v, *CONTAINER_RES, *INDICATOR_RES)) return true
        val cn = v.javaClass.name
        return CONTAINER_CLASS_HINTS.any { cn.contains(it, true) }
    }

    /** 从频道文本往上找到这一整个 Tab 容器 */
    private fun findTabHolder(tv: TextView): View? {
        var cur: View? = tv
        var depth = 0
        while (cur != null && depth < 4) {
            val p = cur.parent as? View ?: return cur
            // Tab 通常是一层包裹，宽度接近父容器
            if (p is android.view.ViewGroup && p.width > 0 && cur.width >= p.width * 0.5f) {
                return cur
            }
            if (isTopBarContainer(p)) return cur
            cur = p
            depth++
        }
        return cur
    }
}
