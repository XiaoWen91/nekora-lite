package top.nekora.clone.state

/**
 * 配置键定义与访问门面。
 *
 * 底层用 [ConfigStore]（外部存储的 Properties 文件），
 * 保证模块 App 与宿主进程读写同一份数据。
 */
object Prefs {

    // ---------- 通用 ----------
    fun getBool(k: String, def: Boolean = false) = ConfigStore.getBool(k, def)
    fun setBool(k: String, v: Boolean) = ConfigStore.setBool(k, v)
    fun getInt(k: String, def: Int = 0) = ConfigStore.getInt(k, def)
    fun setInt(k: String, v: Int) = ConfigStore.setInt(k, v)
    fun getString(k: String, def: String = "") = ConfigStore.getString(k, def)
    fun setString(k: String, v: String) = ConfigStore.setString(k, v)
    fun multiLine(k: String) = ConfigStore.multiLine(k)
    fun setMultiLine(k: String, v: List<String>) = ConfigStore.setMultiLine(k, v)
    fun getSet(k: String) = ConfigStore.getSet(k)
    fun setSet(k: String, v: Set<String>) = ConfigStore.setSet(k, v)

    /**
     * 条目总开关。
     *
     * 默认 true：首次安装时配置文件为空，如果默认 false 所有功能都不生效，
     * 用户看不到任何效果，也就没有动力去打开面板配置。
     * 默认开启后，装完立刻能看到效果，再进面板按需关闭。
     */
    fun isEnabled(item: SettingsItem): Boolean {
        // 只有这几个默认开启：不影响界面观感、改错了也容易发现
        val defaultOn = when (item) {
            SettingsItem.ANTI_RECALL,
            SettingsItem.COMMENT_TIME,
            SettingsItem.DOUBLE_TAP_LIKE -> true
            else -> false
        }
        return ConfigStore.getBool("${item.key}.enabled", defaultOn)
    }
    fun setEnabled(item: SettingsItem, v: Boolean) =
        ConfigStore.setBool("${item.key}.enabled", v)

    // ==================== 键常量 ====================

    // 常用设置
    const val K_FAVORITES = "settings_favorites"

    // 账号
    const val K_USER_ID = "login_user_id"
    const val K_USER_NAME = "login_user_name"
    const val K_USER_KWAI = "login_user_kwai_id"

    // 视频筛选（对应原版 video-filter-thresholds）
    const val K_VF_THRESHOLD = "vf_threshold_enabled"
    const val K_VF_MIN_LIKE = "vf_min_like"
    const val K_VF_MIN_COMMENT = "vf_min_comment"
    const val K_VF_MIN_COLLECT = "vf_min_collect"
    const val K_VF_VIDEO = "vf_video_enabled"
    const val K_VF_IMAGE_TEXT = "vf_image_text_enabled"
    const val K_VF_SHORT_DRAMA = "vf_short_drama_enabled"
    const val K_VF_FALLBACK = "vf_fallback_enabled"

    // 视频信息浮层
    const val K_CAP_TIME = "caption_publish_time"
    const val K_CAP_LOC = "caption_location"
    const val K_CAP_PROGRESS = "caption_playback_progress"
    const val K_CAP_CACHE_TIME = "caption_cache_time"
    const val K_CAP_CACHE_LOC = "caption_cache_location"
    const val K_CAP_CACHE_PROGRESS = "caption_cache_progress"

    // 弹窗拦截
    const val K_POPUP_BLOCKED = "popup_blocked_ids"
    const val K_POPUP_SEEN = "popup_seen_ids"

    // 顶栏
    const val K_TOP_BAR_TABS = "top_bar_hidden_tabs"

    // 底栏
    const val K_BOTTOM_SHOOT_HIDDEN = "bottom_shoot_hidden"
    const val K_BOTTOM_SHOOT_DOUBLE = "bottom_shoot_double_tap"
    const val K_BOTTOM_BAR_ALL_HIDDEN = "bottom_bar_all_hidden"
    const val K_GESTURE_BAR_HIDDEN = "gesture_bar_hidden"

    // 侧边栏
    const val K_SIDE_GROUPS = "side_menu_hidden_groups"
    const val K_SIDE_ITEMS = "side_menu_hidden_items"
    const val K_SIDE_BG_MODE = "side_menu_bg_mode"        // 0 纯色 1 图片
    const val K_SIDE_BG_COLOR = "side_menu_bg_color"
    const val K_SIDE_BG_ALPHA = "side_menu_bg_alpha"      // 0..100
    const val K_SIDE_CARD_ALPHA = "side_menu_card_alpha"  // 0..100
    const val K_SIDE_BG_IMAGE = "side_menu_bg_image"

    // 评论区
    const val K_COMMENT_BG_MODE = "comment_bg_mode"
    const val K_COMMENT_BG_IMAGE = "comment_bg_image"
    const val K_COMMENT_TEXT_LIGHT = "comment_bg_text_light"
    const val K_COMMENT_BLACKLIST = "comment_blacklist"
    const val K_COMMENT_MIN_LEN = "comment_min_len"
    const val K_COMMENT_REGIONS = "comment_regions"
    const val K_COMMENT_ADS = "comment_block_ads"

    // 消息净化
    const val K_NOTICE_FRIEND = "notice_block_friend_reco"
    const val K_NOTICE_CONTACT = "notice_hide_contact_card"
    const val K_NOTICE_PYMK = "notice_hide_pymk"
    const val K_NOTICE_PYMK_USERS = "notice_hide_pymk_users"
    const val K_NOTICE_MARK_ALL_READ = "notice_mark_all_read"

    // 换肤
    const val K_SKIN_COLOR = "settings_skin_color"
    const val DEFAULT_SKIN = 0xFFFF2D55.toInt()

    // 一键已读触发位（面板点击 → 宿主轮询）
    const val K_MARK_ALL_READ_REQUEST = "__mark_all_read_request"
}
