package top.nekora.clone.state

/**
 * 二级面板枚举 —— 完整对应原版 pz0（10 个）。
 *
 * 原版 pz0.java：
 *   0 VideoFilter        视频筛选
 *   1 CaptionOverlay     视频信息浮层
 *   2 RewardAd           奖励广告（本项目不做）
 *   3 CommentBackground  评论区背景
 *   4 TopBarTab          顶栏频道
 *   5 BottomBar          底栏与手势条
 *   6 SideMenu           侧边栏选项
 *   7 SideMenuBg         侧边栏背景
 *   8 NoticeClean        消息净化
 *   9 Skin               设置页换肤
 */
enum class Panel(val title: String) {
    VIDEO_FILTER("视频筛选"),
    CAPTION_OVERLAY("视频信息 Overlay"),
    COMMENT_BG("评论区背景美化"),
    TOP_BAR("顶栏频道隐藏"),
    BOTTOM_BAR("底栏与手势条"),
    SIDE_MENU("侧边栏选项隐藏"),
    SIDE_MENU_BG("侧边栏背景"),
    NOTICE_CLEAN("消息与通知净化"),
    SKIN("设置页面换肤"),
    ;

    companion object {
        fun of(item: SettingsItem): Panel? = when (item) {
            SettingsItem.VIDEO_FILTER -> VIDEO_FILTER
            SettingsItem.CAPTION_OVERLAY -> CAPTION_OVERLAY
            SettingsItem.COMMENT_BG -> COMMENT_BG
            SettingsItem.TOP_BAR -> TOP_BAR
            SettingsItem.BOTTOM_BAR -> BOTTOM_BAR
            SettingsItem.SIDE_MENU -> SIDE_MENU
            SettingsItem.SIDE_MENU_BG -> SIDE_MENU_BG
            SettingsItem.NOTICE_CLEAN -> NOTICE_CLEAN
            SettingsItem.SETTINGS_SKIN -> SKIN
            else -> null
        }
    }
}

/**
 * 面板内的控件类型。
 * 严格对应原版每个面板里实际有的控件。
 */
enum class RowKind { SWITCH, INT_FIELD, TEXT_FIELD, MULTI_TEXT, SLIDER, CHOICE, IMAGE, INFO }

data class PanelRow(
    val kind: RowKind,
    val title: String,
    val desc: String = "",
    val key: String = "",
    /** 默认值 */
    val def: Any? = null,
    /** CHOICE 的选项：显示名 to 值 */
    val choices: List<Pair<String, Int>> = emptyList(),
    /** 提示文案 */
    val hint: String = "",
)

/**
 * 10 个面板的完整结构定义。
 *
 * 全部依据反编译结果：
 *   视频筛选   ← kd1（6 字段）+ C0734a/bx0（控件）
 *   视频信息   ← C0620a（4 字段）
 *   评论区背景 ← 字符串还原
 *   顶栏       ← C0537a（8 字段）
 *   底栏       ← td1（7 字段）+ wd0 字符串
 *   侧边栏选项 ← w01（3 字段）
 *   侧边栏背景 ← r01（6 字段）+ f20.m2703a 字符串
 *   消息净化   ← nc0（6 字段）+ mp 字符串
 *   换肤       ← 字符串还原
 */
object PanelSpec {

    fun rowsOf(panel: Panel): List<PanelRow> = when (panel) {

        // ========== 视频筛选（kd1 + C0734a/bx0）==========
        Panel.VIDEO_FILTER -> listOf(
            PanelRow(RowKind.INFO, "说明", "设为 0 时不限；短剧不参与阈值判断。"),
            PanelRow(RowKind.SWITCH, "筛选普通视频", "按下方互动阈值筛选", Prefs.K_VF_VIDEO, true),
            PanelRow(RowKind.SWITCH, "筛选图文", "对图文作品使用相同阈值", Prefs.K_VF_IMAGE_TEXT, true),
            PanelRow(RowKind.INT_FIELD, "最低点赞数", "", Prefs.K_VF_MIN_LIKE, 0, hint = "0 = 不限"),
            PanelRow(RowKind.INT_FIELD, "最低评论数", "", Prefs.K_VF_MIN_COMMENT, 0, hint = "0 = 不限"),
            PanelRow(RowKind.INT_FIELD, "最低收藏数", "", Prefs.K_VF_MIN_COLLECT, 0, hint = "0 = 不限"),
            PanelRow(RowKind.SWITCH, "直接过滤短剧", "开启后直接过滤短剧内容", Prefs.K_VF_SHORT_DRAMA, false),
            PanelRow(
                RowKind.SWITCH, "保留接近阈值的内容",
                "低通过率时，最多额外保留 3 条普通视频", Prefs.K_VF_FALLBACK, false
            ),
        )

        // ========== 视频信息浮层（C0620a）==========
        Panel.CAPTION_OVERLAY -> listOf(
            PanelRow(RowKind.SWITCH, "发布时间", "显示作品发布时间", Prefs.K_CAP_TIME, false),
            PanelRow(RowKind.SWITCH, "发布地", "显示 IP 属地 / 发布地点", Prefs.K_CAP_LOC, false),
            PanelRow(RowKind.SWITCH, "播放进度", "显示当前播放位置 / 总时长", Prefs.K_CAP_PROGRESS, false),
        )

        // ========== 评论区背景 ==========
        Panel.COMMENT_BG -> listOf(
            PanelRow(
                RowKind.CHOICE, "背景模式", "深色纯色或自定义图片",
                Prefs.K_COMMENT_BG_MODE, 0,
                choices = listOf("深色纯色" to 0, "自定义图片" to 1)
            ),
            PanelRow(RowKind.IMAGE, "背景图片", "已选择图片，点击可更换；重新打开评论面板生效",
                Prefs.K_COMMENT_BG_IMAGE, ""),
            PanelRow(
                RowKind.CHOICE, "文字颜色", "文字颜色固定不变，图片上会叠一层蒙版",
                Prefs.K_COMMENT_TEXT_LIGHT, 1,
                choices = listOf("浅色文字" to 1, "深色文字" to 0)
            ),
            PanelRow(
                RowKind.INFO, "说明",
                "浅色文字配深色蒙版，适合大多数图片；深色文字配浅色蒙版，适合偏亮的图片"
            ),
        )

        // ========== 顶栏频道（C0537a）==========
        Panel.TOP_BAR -> listOf(
            PanelRow(RowKind.SWITCH, "启用顶栏频道隐藏", "", "top_bar.enabled", false),
            PanelRow(RowKind.INFO, "可隐藏的频道",
                "热门 / 精选 / 发现 / 直播 / 同城 / 商城 / 团购 / 小说 / 放映厅 / 游戏 / 短剧 / 小游戏 / 招聘 / 生活 / 朋友 / 关注"),
            PanelRow(RowKind.MULTI_TEXT, "隐藏频道名（每行一个）", "与顶栏文字完全匹配才隐藏",
                Prefs.K_TOP_BAR_TABS, ""),
            PanelRow(RowKind.SWITCH, "启用顶栏残留条目清理",
                "用于移除旧 View 缓存中残留的顶栏频道容器", "top_bar.cleanup", false),
        )

        // ========== 底栏与手势条（td1 + wd0）==========
        Panel.BOTTOM_BAR -> listOf(
            PanelRow(RowKind.SWITCH, "加号改为双击打开",
                "单击中间拍摄按钮不再有反应，300 毫秒内点第二次才打开（防误触）",
                Prefs.K_BOTTOM_SHOOT_DOUBLE, false),
            PanelRow(RowKind.SWITCH, "隐藏加号",
                "隐藏中间的拍摄按钮，四个 Tab 平分整条底栏，不留空档",
                Prefs.K_BOTTOM_SHOOT_HIDDEN, false),
            PanelRow(RowKind.SWITCH, "隐藏整个底栏",
                "连同四个 Tab 一起隐藏，视频延伸到底部；隐藏后没有切页入口，只能靠侧滑或返回键",
                Prefs.K_BOTTOM_BAR_ALL_HIDDEN, false),
            PanelRow(RowKind.INFO, "手势条控制",
                "独立控制系统手势条的隐藏与底部布局；完整重启快手生效"),
            PanelRow(RowKind.SWITCH, "隐藏手势条", "整条系统手势条隐藏",
                Prefs.K_GESTURE_BAR_HIDDEN, false),
        )

        // ========== 侧边栏选项（w01）==========
        Panel.SIDE_MENU -> listOf(
            PanelRow(RowKind.SWITCH, "启用侧边栏选项隐藏", "", "side_menu.enabled", false),
            PanelRow(RowKind.MULTI_TEXT, "隐藏的分组（每行一个）",
                "可选：topList / commonlyUsedList / moreList / toolList / creativeList / teenageModeList",
                Prefs.K_SIDE_GROUPS, ""),
            PanelRow(RowKind.MULTI_TEXT, "隐藏的选项（每行一个）", "按侧边栏文字匹配",
                Prefs.K_SIDE_ITEMS, ""),
        )

        // ========== 侧边栏背景（r01 + f20.m2703a）==========
        Panel.SIDE_MENU_BG -> listOf(
            PanelRow(RowKind.SWITCH, "启用侧边栏背景",
                "改侧边栏底色 / 背景图与透明度；完整重启快手生效",
                "side_menu_bg.enabled", false),
            PanelRow(
                RowKind.CHOICE, "背景模式", "",
                Prefs.K_SIDE_BG_MODE, 0,
                choices = listOf("纯色" to 0, "自定义图片" to 1)
            ),
            PanelRow(RowKind.IMAGE, "背景图片",
                "已选择图片，点击可更换；重启快手生效", Prefs.K_SIDE_BG_IMAGE, ""),
            PanelRow(RowKind.CHOICE, "背景颜色", "纯色模式下的底色",
                Prefs.K_SIDE_BG_COLOR, 0xFFF7F7FA.toInt(),
                choices = listOf(
                    "浅灰" to 0xFFF7F7FA.toInt(),
                    "纯白" to 0xFFFFFFFF.toInt(),
                    "深灰" to 0xFF2C2C2E.toInt(),
                    "纯黑" to 0xFF000000.toInt(),
                    "深海蓝" to 0xFF1E3A8A.toInt(),
                )
            ),
            PanelRow(RowKind.SLIDER, "背景不透明度", "", Prefs.K_SIDE_BG_ALPHA, 100),
            PanelRow(RowKind.SLIDER, "分组卡片不透明度",
                "侧边栏每个分组自带白底；调低才能看见背景", Prefs.K_SIDE_CARD_ALPHA, 100),
        )

        // ========== 消息净化（nc0 + mp）==========
        Panel.NOTICE_CLEAN -> listOf(
            PanelRow(RowKind.SWITCH, "启用消息与通知净化",
                "总开关；关闭时消息与通知保持快手原样，完整重启生效",
                "notice_clean.enabled", false),
            PanelRow(RowKind.SWITCH, "隐藏「可能认识的人」",
                "好友推荐通知从列表里移除；点赞、评论、@ 不受影响",
                Prefs.K_NOTICE_FRIEND, false),
            PanelRow(RowKind.SWITCH, "隐藏通讯录引流卡",
                "私信列表顶部「发现通讯录好友」那一条", Prefs.K_NOTICE_CONTACT, false),
            PanelRow(RowKind.SWITCH, "隐藏推荐关注区",
                "私信列表底部「可能感兴趣的人」标题栏", Prefs.K_NOTICE_PYMK, false),
            PanelRow(RowKind.SWITCH, "隐藏推荐用户",
                "标题栏下面一行行带关注按钮的推荐用户；隐藏后只加载首屏一页",
                Prefs.K_NOTICE_PYMK_USERS, false),
            PanelRow(RowKind.SWITCH, "「+」菜单显示一键已读",
                "消息页右上角「+」里加一项，点了先确认，再把所有私信标为已读；不受下方总开关影响",
                Prefs.K_NOTICE_MARK_ALL_READ, false),
        )

        // ========== 设置页换肤 ==========
        Panel.SKIN -> listOf(
            PanelRow(RowKind.SWITCH, "启用设置页换肤",
                "对快手设置页应用主题色（状态栏/导航栏/标题栏），重新进入设置页生效",
                "settings_skin.enabled", false),
            PanelRow(RowKind.CHOICE, "主题色", "",
                Prefs.K_SKIN_COLOR, Prefs.DEFAULT_SKIN,
                choices = listOf(
                    "快手红" to 0xFFFF2D55.toInt(),
                    "松石绿" to 0xFF12B886.toInt(),
                    "葡萄紫" to 0xFF7C4DFF.toInt(),
                    "曜石黑" to 0xFF1F1F1F.toInt(),
                )
            ),
        )
    }

    /** 面板的说明副标题 */
    fun subtitleOf(panel: Panel): String = when (panel) {
        Panel.VIDEO_FILTER -> "按互动阈值筛选内容，短剧可独立直滤"
        Panel.CAPTION_OVERLAY -> "在播放页显示发布时间、发布地、播放进度"
        Panel.COMMENT_BG -> "背景美化 · 评论过滤 · 自定义评论"
        Panel.TOP_BAR -> "隐藏不需要的顶部频道"
        Panel.BOTTOM_BAR -> "拍摄按钮、底栏与系统手势条"
        Panel.SIDE_MENU -> "隐藏侧边栏里的分组和选项"
        Panel.SIDE_MENU_BG -> "底色 / 背景图与透明度"
        Panel.NOTICE_CLEAN -> "好友推荐、通讯录卡与推荐用户"
        Panel.SKIN -> "对快手设置页应用主题色"
    }
}
