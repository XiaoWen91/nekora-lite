package top.nekora.clone.hook.feature

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.ViewFinder
import top.nekora.clone.util.ViewHider

/**
 * 消息与通知净化（完整还原原版 mp.java [KwaiNoticeClean]）。
 *
 * **关键：必须先找到 message_root_layout 作为入口，再在它子树里找。**
 * 我此前直接遍历整个 View 树，所以一个都找不到。
 *
 * 原版 m3860a 的完整流程：
 *   1. View root = find(viewGroup, "message_root_layout");
 *      if (!(root instanceof ViewGroup)) return;     ← 找不到就直接放弃
 *   2. if (开关1) hide(find(root, "contact_layout"),  "contact-card");
 *   3. if (开关2) hide(find(root, "close_pymk"),      "pymk");
 *   4. if (开关3) {
 *          // 遍历 root 子 View，找含 follower_layout 的
 *          for (int i = 0; i < root.getChildCount(); i++) {
 *              View child = root.getChildAt(i);
 *              // 跳过已隐藏的（GONE 且 height==0）
 *              if (child.getVisibility() != 8 || lp.height != 0) {
 *                  if (find(child, "follower_layout") != null) hide(child, "pymk-user");
 *              }
 *          }
 *          hide(find(root, "pymkFooterLayout"), "pymk-footer");
 *      }
 */
object NoticeCleanHook {

    private const val TAG = "[NoticeClean]"

    // 原版资源名常量
    private const val RES_ROOT = "message_root_layout"
    private const val RES_CONTACT = "contact_layout"
    private const val RES_PYMK = "close_pymk"
    private const val RES_FOLLOWER = "follower_layout"
    private const val RES_PYMK_FOOTER = "pymkFooterLayout"

    private var appCtx: Context? = null

    fun install(module: XposedModule, pkg: String) {
        hookAppAttach(module)
        hookViewAttach(module)
        hookRecyclerScroll(module)
        Log.hooked("$TAG install pkg=$pkg")
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

    private fun hookViewAttach(module: XposedModule) {
        val m = runCatching {
            View::class.java.getDeclaredMethod("onAttachedToWindow").apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val r = chain.proceed()
            val v = chain.thisObject as? ViewGroup
            if (v != null && Prefs.isEnabled(SettingsItem.NOTICE_CLEAN)) {
                runCatching { clean(v) }
                    .onFailure { Log.w("$TAG clean: ${it.message}") }
            }
            r
        }
        Log.i("$TAG hooked View.onAttachedToWindow")
    }

    /** RecyclerView 滚动时也要重新清理（复用时会影响） */
    private fun hookRecyclerScroll(module: XposedModule) {
        val m = runCatching {
            View::class.java.getDeclaredMethod("onScrollChanged", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType).apply { isAccessible = true }
        }.getOrNull() ?: return
        // 频率太高，跳过；保留接口
    }

    // ==================== 原版 m3860a 的完整还原 ====================

    private fun clean(host: ViewGroup) {
        // 1. 必须先找到 message_root_layout
        val root = find(host, RES_ROOT) as? ViewGroup
        if (root == null) {
            // 不是消息页，不处理（避免全 app 遍历）
            return
        }
        Log.i("$TAG 找到 message_root_layout，开始清理")

        // 2. 通讯录卡
        if (Prefs.getBool(Prefs.K_NOTICE_CONTACT)) {
            find(root, RES_CONTACT)?.let {
                ViewHider.hideHard(it)
                Log.i("$TAG hidden contact-card")
            }
        }

        // 3. 推荐关注区
        if (Prefs.getBool(Prefs.K_NOTICE_PYMK)) {
            find(root, RES_PYMK)?.let {
                ViewHider.hideHard(it)
                Log.i("$TAG hidden pymk")
            }
        }

        // 4. 推荐用户行 + 页脚
        if (Prefs.getBool(Prefs.K_NOTICE_PYMK_USERS)) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i) ?: continue
                // 跳过已隐藏的（原版判断：GONE 且 height==0）
                val lp = child.layoutParams
                val alreadyHidden = child.visibility == View.GONE && lp != null && lp.height == 0
                if (alreadyHidden) continue

                if (find(child, RES_FOLLOWER) != null) {
                    ViewHider.hideHard(child)
                    Log.i("$TAG hidden pymk-user (child#$i)")
                }
            }
            find(root, RES_PYMK_FOOTER)?.let {
                ViewHider.hideHard(it)
                Log.i("$TAG hidden pymk-footer")
            }
        }
    }

    /** 原版 m3865a：按资源名找 View */
    private fun find(root: View, resName: String): View? {
        val ctx = runCatching { root.context }.getOrNull() ?: return null
        val id = runCatching {
            ctx.resources.getIdentifier(resName, "id", ctx.packageName)
        }.getOrDefault(0)
        if (id == 0) return null
        return runCatching { root.findViewById<View>(id) }.getOrNull()
    }
}
