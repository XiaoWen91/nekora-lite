package top.nekora.clone.dexkit

import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 静态符号表 —— DexKit 的降级方案。
 *
 * 依据实际日志：[SelfCheck] 类命中 20/21，说明这个快手版本
 * 类名基本可以直接命中，根本不需要 DexKit。
 *
 * 只有快手大版本升级、类名全变时才需要 DexKit 兜底。
 * 所以设计成：先查这张表，查不到再走 DexKit。
 */
object SymbolTable {

    private const val TAG = "[Symbol]"

    /** 已经过日志验证存在的类（20/21 命中） */
    private val STATIC = mapOf(
        // 设置页
        "settings.itemList" to arrayOf(
            "com.yxcorp.plugin.setting.stencil.SettingItemListStencilFragment",
        ),
        "settings.activity" to arrayOf(
            "com.yxcorp.plugin.setting.activity.SettingsActivity",
        ),
        // 分享
        "share.panel" to arrayOf(
            "com.yxcorp.gifshow.share.widget.ForwardGridSectionFragment",
            "com.yxcorp.gifshow.share.growth.immerse.ImmerseSharePanelFragment",
        ),
        "share.element" to arrayOf(
            "com.kwai.sharelib.model.ShareInitResponse\$SharePanelElement",
        ),
        // 评论
        "comment.panel" to arrayOf(
            "com.kwai.component.tabs.panel.widget.TabsPanelNestedParentRelativeLayout",
        ),
        "comment.model" to arrayOf(
            "com.kuaishou.android.model.mix.QComment",
        ),
        "comment.list" to arrayOf(
            "com.kwai.kmp.comment.mvi.panel.pagelist.KmpCommentFeaturePageList",
        ),
        // 底栏
        "bottombar" to arrayOf(
            "com.kwai.kcube.ext.actionbar.bottom.BottomActionBar",
        ),
        "bottombar.shoot" to arrayOf(
            "com.kwai.feature.api.feed.home.wrapper.kcube.widget.BottomBarShootView",
        ),
        "bottombar.tab" to arrayOf(
            "com.google.android.material.tabs.BottomActionBarTabLayout",
        ),
        // 侧边栏
        "sidemenu.pane" to arrayOf(
            "androidx.slidingpanelayout.widget.KwaiSlidingPaneLayout",
        ),
        // 顶栏
        "topbar.layout" to arrayOf(
            "com.kwai.kcube.ext.widget.TabAutoSizeLayout",
        ),
        "topbar.model" to arrayOf(
            "com.kwai.component.kcube.model.model.TabModel",
        ),
        "topbar.config" to arrayOf(
            "com.kwai.component.kcube.model.model.TabConfig",
        ),
        // 消息
        "im.conv" to arrayOf(
            "com.kwai.kmp.imsdk.internal.logic.conversation.IMConversationManager",
        ),
        // 弹窗
        "popup" to arrayOf(
            "com.kwai.library.widget.popup.common.Popup",
        ),
    )

    private val cache = HashMap<String, Class<*>?>()

    /**
     * 按符号名查类：先查静态表，失败返回 null（交给 DexKit）。
     */
    fun find(key: String, loader: ClassLoader): Class<*>? {
        cache[key]?.let { return it }

        val names = STATIC[key] ?: return null
        for (name in names) {
            val cls = Reflect.findClass(loader, name)
            if (cls != null) {
                cache[key] = cls
                Log.i("$TAG $key -> ${cls.simpleName}（静态表）")
                return cls
            }
        }
        cache[key] = null
        Log.i("$TAG $key 静态表未命中，需要 DexKit")
        return null
    }

    /** 这张表覆盖了多少符号 */
    fun size(): Int = STATIC.size
}
