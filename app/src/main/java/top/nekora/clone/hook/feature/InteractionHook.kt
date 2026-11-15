package top.nekora.clone.hook.feature

import android.app.Application
import android.content.Context
import android.view.View
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log

/**
 * 交互与防误触（完整还原原版 yn.java [KwaiLikeConfirm]）。
 *
 * **关键点（此前写错的）：**
 *   1. 资源名清单是精确的，不能随便加兜底：
 *        LIKE    = ["like_button", "like_element_click_layout"]
 *        COLLECT = ["collect_button"]
 *      我之前加了 "like" / "collect" 当兜底，会误判一堆普通 View。
 *
 *   2. 「是否已点赞」靠 contentDescription 判断：
 *        CharSequence cd = view.getContentDescription();
 *        if (cd != null && cd.toString().trim().contains("已")) → 已点赞
 *      不是读字段，字段名混淆后不可靠。
 *
 *   3. 资源 id 要缓存，原版用 ConcurrentHashMap 缓存，避免每次 getIdentifier。
 */
object InteractionHook {

    private const val TAG = "[Interaction]"

    /** 原版 f11721a —— 精确 2 个 */
    private val LIKE_RES = listOf("like_button", "like_element_click_layout")

    /** 原版 f11722a —— 精确 1 个 */
    private val COLLECT_RES = listOf("collect_button")

    /** 原版 f11720a —— 判断已点赞的标志 */
    private const val LIKED_FLAG = "已"

    private var appCtx: Context? = null
    private var hostPkg: String = ""

    /** 原版 f11726a：资源名 → id 的缓存 */
    private val idCache = HashMap<String, Int>()

    private var lastLike = 0L
    private var lastCollect = 0L
    private const val DOUBLE_MS = 400L

    fun install(module: XposedModule, pkg: String) {
        hostPkg = pkg
        hookAppAttach(module)
        hookPerformClick(module)
        hookPerformLongClick(module)
        Log.hooked("$TAG install pkg=$pkg res=like_button/collect_button")
    }

    private fun hookAppAttach(module: XposedModule) {
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
    }

    private fun hookPerformClick(module: XposedModule) {
        val m = runCatching {
            View::class.java.getDeclaredMethod("performClick").apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val v = chain.thisObject as? View ?: return@intercept chain.proceed()

            val isLike = matchAny(v, LIKE_RES)
            val isCollect = !isLike && matchAny(v, COLLECT_RES)

            when {
                // 屏蔽双击点赞：中央双击不点赞
                isLike && Prefs.isEnabled(SettingsItem.DOUBLE_TAP_LIKE) && fromCenter(v) -> {
                    Log.i("$TAG blocked center double-tap like")
                    true
                }
                // 点赞改双击确认
                isLike && Prefs.isEnabled(SettingsItem.LIKE_CONFIRM) -> {
                    val now = System.currentTimeMillis()
                    if (now - lastLike <= DOUBLE_MS) {
                        lastLike = 0L
                        Log.i("$TAG like confirmed")
                        chain.proceed()
                    } else {
                        lastLike = now
                        Log.i("$TAG like first tap swallowed")
                        false
                    }
                }
                // 收藏改双击确认
                isCollect && Prefs.isEnabled(SettingsItem.COLLECT_CONFIRM) -> {
                    val now = System.currentTimeMillis()
                    if (now - lastCollect <= DOUBLE_MS) {
                        lastCollect = 0L
                        Log.i("$TAG collect confirmed")
                        chain.proceed()
                    } else {
                        lastCollect = now
                        Log.i("$TAG collect first tap swallowed")
                        false
                    }
                }
                else -> chain.proceed()
            }
        }
        Log.i("$TAG hooked View.performClick")
    }

    private fun hookPerformLongClick(module: XposedModule) {
        val m = runCatching {
            View::class.java.getDeclaredMethod("performLongClick").apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val v = chain.thisObject as? View ?: return@intercept chain.proceed()

            if (Prefs.isEnabled(SettingsItem.LONG_PRESS_LIKE) &&
                matchAny(v, LIKE_RES) &&
                !isLiked(v)          // 已点赞时允许长按取消
            ) {
                Log.i("$TAG blocked long-press like")
                return@intercept true
            }
            chain.proceed()
        }
        Log.i("$TAG hooked View.performLongClick")
    }

    // ==================== 判定 ====================

    /** 原版 m5780a：按资源 id 匹配，带缓存 */
    private fun matchAny(v: View, resNames: List<String>): Boolean {
        if (v.id == View.NO_ID) return false
        val pkg = hostPkg.ifEmpty {
            appCtx?.packageName ?: v.context?.packageName ?: return false
        }
        for (name in resNames) {
            val id = idCache.getOrPut(name) {
                runCatching {
                    v.resources.getIdentifier(name, "id", pkg)
                }.getOrDefault(0)
            }
            if (id != 0 && id == v.id) return true
        }
        return false
    }

    /** 原版 m5777a：contentDescription 含「已」= 已点赞 */
    private fun isLiked(v: View): Boolean = runCatching {
        val cd = v.contentDescription?.toString()?.trim() ?: return false
        cd.contains(LIKED_FLAG)
    }.getOrDefault(false)

    /** 中央手势触发的点赞：父链里有 player / gesture / surface */
    private fun fromCenter(v: View): Boolean {
        var p: android.view.ViewParent? = v.parent
        var d = 0
        while (p != null && d < 6) {
            val n = (p as? View)?.javaClass?.name?.lowercase() ?: ""
            if (n.contains("gesture") || n.contains("player") || n.contains("surface")) return true
            p = p.parent
            d++
        }
        return false
    }
}
