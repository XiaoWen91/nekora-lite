package top.nekora.clone.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 模块 App 内的界面状态（单向数据流）。
 * 每次写操作同步落盘到 Prefs，宿主下次读取即可生效。
 */
class AppState {

    // 顶栏
    var topBarEnabled by mutableStateOf(Prefs.topBarHideEnabled)
        private set
    var topBarTabs by mutableStateOf(Prefs.hiddenTopBarTabs)
        private set

    // 底栏
    var hideShoot by mutableStateOf(Prefs.bottomBarHideShoot)
        private set
    var shootDoubleTap by mutableStateOf(Prefs.bottomBarShootDoubleTap)
        private set
    var hideBottomBar by mutableStateOf(Prefs.bottomBarHidden)
        private set

    // 侧边栏
    var sideMenuEnabled by mutableStateOf(Prefs.sideMenuHideEnabled)
        private set
    var sideMenuGroups by mutableStateOf(Prefs.hiddenSideMenuGroups)
        private set
    var sideMenuItems by mutableStateOf(Prefs.hiddenSideMenuItems)
        private set

    // 评论过滤
    var commentFilterEnabled by mutableStateOf(Prefs.commentFilterEnabled)
        private set
    var commentBlacklist by mutableStateOf(Prefs.commentBlacklist.joinToString("\n"))
        private set
    var commentMinLen by mutableStateOf(Prefs.commentMinLen.toString())
        private set
    var commentRegions by mutableStateOf(Prefs.commentBlockRegions.joinToString("\n"))
        private set
    var commentBlockAds by mutableStateOf(Prefs.commentBlockAds)
        private set

    // Feed
    var feedAdFilter by mutableStateOf(Prefs.feedAdFilterEnabled)
        private set

    // 撤回
    var antiRecall by mutableStateOf(Prefs.antiRecallEnabled)
        private set

    // 浮层
    var captionOverlay by mutableStateOf(Prefs.captionOverlayEnabled)
        private set

    // 换肤
    var skinEnabled by mutableStateOf(Prefs.skinEnabled)
        private set
    var skinColor by mutableStateOf(Prefs.skinColor)
        private set

    // ---------- 写入 ----------
    fun setTopBarEnabled(v: Boolean) { topBarEnabled = v; Prefs.topBarHideEnabled = v }
    fun toggleTopBarTab(id: String) {
        val next = topBarTabs.toMutableSet().apply { if (!add(id)) remove(id) }
        topBarTabs = next
        Prefs.hiddenTopBarTabs = next
    }

    fun setHideShoot(v: Boolean) { hideShoot = v; Prefs.bottomBarHideShoot = v }
    fun setShootDoubleTap(v: Boolean) { shootDoubleTap = v; Prefs.bottomBarShootDoubleTap = v }
    fun setHideBottomBar(v: Boolean) { hideBottomBar = v; Prefs.bottomBarHidden = v }

    fun setSideMenuEnabled(v: Boolean) { sideMenuEnabled = v; Prefs.sideMenuHideEnabled = v }
    fun toggleSideMenuGroup(k: String) {
        val next = sideMenuGroups.toMutableSet().apply { if (!add(k)) remove(k) }
        sideMenuGroups = next
        Prefs.hiddenSideMenuGroups = next
    }
    fun toggleSideMenuItem(k: String) {
        val next = sideMenuItems.toMutableSet().apply { if (!add(k)) remove(k) }
        sideMenuItems = next
        Prefs.hiddenSideMenuItems = next
    }

    fun setCommentFilter(v: Boolean) { commentFilterEnabled = v; Prefs.commentFilterEnabled = v }
    fun setCommentBlacklist(v: String) {
        commentBlacklist = v
        Prefs.commentBlacklist = v.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
    fun setCommentMinLen(v: String) {
        commentMinLen = v.filter { it.isDigit() }
        Prefs.commentMinLen = commentMinLen.toIntOrNull() ?: 0
    }
    fun setCommentRegions(v: String) {
        commentRegions = v
        Prefs.commentBlockRegions = v.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }
    fun setCommentBlockAds(v: Boolean) { commentBlockAds = v; Prefs.commentBlockAds = v }

    fun setFeedAdFilter(v: Boolean) { feedAdFilter = v; Prefs.feedAdFilterEnabled = v }
    fun setAntiRecall(v: Boolean) { antiRecall = v; Prefs.antiRecallEnabled = v }
    fun setCaptionOverlay(v: Boolean) { captionOverlay = v; Prefs.captionOverlayEnabled = v }
    fun setSkinEnabled(v: Boolean) { skinEnabled = v; Prefs.skinEnabled = v }
    fun setSkinColor(v: Int) { skinColor = v; Prefs.skinColor = v }

    /** 顶栏可隐藏频道候选（快手常见频道） */
    val topBarCandidates = listOf(
        "热门", "精选", "发现", "直播", "同城", "商城", "团购", "小说",
        "放映厅", "游戏", "短剧", "小游戏", "招聘", "生活", "朋友", "关注",
    )

    /** 侧边栏分组候选 */
    val sideMenuGroupCandidates = listOf(
        "topList" to "顶部入口",
        "commonlyUsedList" to "常用功能",
        "moreList" to "更多",
        "toolList" to "工具",
        "creativeList" to "创作",
        "teenageModeList" to "青少年模式",
    )
}
