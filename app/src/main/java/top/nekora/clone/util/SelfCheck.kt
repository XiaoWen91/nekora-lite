package top.nekora.clone.util

import android.app.Activity
import android.content.Context
import top.nekora.clone.hook.entry.PanelHost

/**
 * 启动自检：把所有依赖的类名、资源名一次性探一遍。
 *
 * 目的：用一条日志告诉使用者
 *   - 哪些类在当前快手版本存在
 *   - 哪些资源名存在
 *   - 当前进程能不能拿到 Activity
 *
 * 这样不用猜，直接看日志就知道该改什么。
 */
object SelfCheck {

    private const val TAG = "[SelfCheck]"

    /** 原版依赖的关键类 */
    private val CLASSES = listOf(
        // 设置页
        "com.yxcorp.plugin.setting.stencil.SettingItemListStencilFragment",
        "com.yxcorp.gifshow.settings.page.SettingItemListFragment",
        "com.yxcorp.plugin.setting.activity.SettingsActivity",
        // 分享页
        "com.yxcorp.gifshow.share.widget.ForwardGridSectionFragment",
        "com.yxcorp.gifshow.share.growth.immerse.ImmerseSharePanelFragment",
        "com.kwai.sharelib.model.ShareInitResponse\$SharePanelElement",
        // 评论
        "com.kwai.component.tabs.panel.widget.TabsPanelNestedParentRelativeLayout",
        "com.yxcorp.gifshow.view.CommentRootConstraintLayout",
        "com.kuaishou.android.model.mix.QComment",
        "com.yxcorp.gifshow.util.DateUtils",
        "com.kwai.kmp.comment.mvi.panel.pagelist.KmpCommentFeaturePageList",
        // 底栏
        "com.kwai.kcube.ext.actionbar.bottom.BottomActionBar",
        "com.kwai.feature.api.feed.home.wrapper.kcube.widget.BottomBarShootView",
        "com.google.android.material.tabs.BottomActionBarTabLayout",
        // 侧边栏
        "androidx.slidingpanelayout.widget.KwaiSlidingPaneLayout",
        // 顶栏
        "com.kwai.kcube.ext.widget.TabAutoSizeLayout",
        "com.kwai.component.kcube.model.model.TabModel",
        "com.kwai.component.kcube.model.model.TabConfig",
        // 消息
        "com.kwai.kmp.imsdk.internal.logic.conversation.IMConversationManager",
        // 弹窗
        "com.kwai.library.widget.popup.common.Popup",
        // 设置页换肤
        "com.yxcorp.plugin.setting.activity.SettingsActivity",
    )

    /** 原版依赖的资源名 */
    private val RES_IDS = listOf(
        // 评论面板
        "tabs_panel_close", "tabs_panel_operate_area", "tabs_panel_container",
        "tabs_panel_comment_survey_container", "list_mask", "tabs_panel_full",
        // 点赞收藏
        "like_button", "like_element_click_layout", "collect_button",
        // 消息净化
        "contact_layout", "close_pymk", "follower_layout",
        "pymkFooterLayout", "message_root_layout", "recycler_view",
        // 顶栏
        "pagerslidingtabstrip", "kcubetabactionbar", "kcubetabstrip",
        "hometop", "topbar", "toptab", "titletab",
        // 底栏
        "bottomactionbar", "bottomnavigation", "bottomtab",
        // 菜单
        "menu_icon",
    )

    /** 由 Application.attach 缓存的 Context */
    @Volatile
    var appCtx: Context? = null

    fun run(loader: ClassLoader, ctx0: Context?, processName: String) {
        val ctx = ctx0 ?: appCtx
        val isMain = !processName.contains(":")

        // ---- 类探测 ----
        val found = ArrayList<String>()
        val missing = ArrayList<String>()
        for (name in CLASSES) {
            val ok = runCatching { Class.forName(name, false, loader) }.isSuccess
            if (ok) found.add(name.substringAfterLast('.')) else missing.add(name.substringAfterLast('.'))
        }
        Log.i("$TAG [$processName] main=$isMain")
        Log.i("$TAG 类命中 ${found.size}/${CLASSES.size}: ${found.joinToString(",")}")
        if (missing.isNotEmpty()) {
            Log.w("$TAG 类缺失 ${missing.size}: ${missing.joinToString(",")}")
        }

        // ---- 资源探测 ----
        if (ctx != null) {
            val resOk = ArrayList<String>()
            val resMissing = ArrayList<String>()
            for (r in RES_IDS) {
                val id = runCatching {
                    ctx.resources.getIdentifier(r, "id", ctx.packageName)
                }.getOrDefault(0)
                if (id != 0) resOk.add("$r=$id") else resMissing.add(r)
            }
            Log.i("$TAG 资源命中 ${resOk.size}/${RES_IDS.size}")
            if (resOk.isNotEmpty()) Log.i("$TAG 资源明细: ${resOk.joinToString(" ")}")
            if (resMissing.isNotEmpty()) {
                Log.w("$TAG 资源缺失 ${resMissing.size}: ${resMissing.joinToString(",")}")
            }
        } else {
            Log.w("$TAG ctx=null，跳过资源探测")
        }

        // ---- Activity 可达性 ----
        val act = PanelHost.topActivity()
        Log.i("$TAG topActivity=${act?.javaClass?.name ?: "null"}")
    }
}
