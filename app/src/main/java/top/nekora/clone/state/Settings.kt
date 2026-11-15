package top.nekora.clone.state

/**
 * 设置项与分组的完整定义。
 *
 * 对照原版 ow0（分组）/ tw0（条目）两个枚举，并补齐截图里出现的全部条目。
 */
enum class SettingsGroup(val title: String, val subtitle: String) {
    VIDEO("视频与浏览", "内容筛选、广告与视频信息"),
    INTERACTION("交互与防误触", "点赞、收藏与手势确认"),
    APPEARANCE("界面与外观", "顶栏、底栏、侧栏与背景"),
    MESSAGES("消息与评论", "消息保留、定时私信与通知"),
    TOOLS("模块与维护", "主题、功能适配与维护"),
}

enum class SettingsItem(
    val key: String,
    val group: SettingsGroup,
    val hasDetail: Boolean,
    val standardOnly: Boolean = false,
    val needsRestart: Boolean = false,
) {
    // ---------- 视频与浏览 ----------
    VIDEO_FILTER("video-filter", SettingsGroup.VIDEO, true),
    VIDEO_AD_BLOCK("video-ad-block", SettingsGroup.VIDEO, false),
    POPUP_AD_BLOCK("popup-ad-block", SettingsGroup.VIDEO, true),
    CAPTION_OVERLAY("caption-overlay", SettingsGroup.VIDEO, true),

    // ---------- 交互与防误触 ----------
    DOUBLE_TAP_LIKE("double-tap-like-block", SettingsGroup.INTERACTION, false),
    LONG_PRESS_LIKE("like-long-press-block", SettingsGroup.INTERACTION, false),
    LIKE_CONFIRM("like-confirm", SettingsGroup.INTERACTION, false),
    COLLECT_CONFIRM("collect-confirm", SettingsGroup.INTERACTION, false),

    // ---------- 界面与外观 ----------
    TOP_BAR("top-bar", SettingsGroup.APPEARANCE, true, needsRestart = true),
    BOTTOM_BAR("bottom-bar", SettingsGroup.APPEARANCE, true, needsRestart = true),
    SIDE_MENU("side-menu", SettingsGroup.APPEARANCE, true, needsRestart = true),
    SIDE_MENU_BG("side-menu-bg", SettingsGroup.APPEARANCE, true, needsRestart = true),
    SETTINGS_SKIN("settings-skin", SettingsGroup.APPEARANCE, true, standardOnly = true),

    // ---------- 消息与评论 ----------
    ANTI_RECALL("chat-anti-recall", SettingsGroup.MESSAGES, false, needsRestart = true),
    COMMENT_BG("comment-bg", SettingsGroup.MESSAGES, true),
    COMMENT_TIME("comment-time", SettingsGroup.MESSAGES, false),
    COMMENT_FILTER("comment-filter", SettingsGroup.MESSAGES, true),
    NOTICE_CLEAN("notice-clean", SettingsGroup.MESSAGES, true, needsRestart = true),
    MARK_ALL_READ("mark-all-read", SettingsGroup.MESSAGES, false),

    // ---------- 模块与维护 ----------
    ABOUT("about", SettingsGroup.TOOLS, true),
    ;

    fun visibleOn(pkg: String): Boolean {
        if (pkg == "com.kuaishou.nebula") return true
        return pkg == "com.smile.gifmaker" && !standardOnly
    }
}

object ItemText {

    fun title(item: SettingsItem): String = when (item) {
        SettingsItem.VIDEO_FILTER -> "视频筛选"
        SettingsItem.VIDEO_AD_BLOCK -> "视频广告拦截"
        SettingsItem.POPUP_AD_BLOCK -> "弹窗广告拦截"
        SettingsItem.CAPTION_OVERLAY -> "视频信息 Overlay"
        SettingsItem.DOUBLE_TAP_LIKE -> "屏蔽双击点赞"
        SettingsItem.LONG_PRESS_LIKE -> "拦截长按点赞"
        SettingsItem.LIKE_CONFIRM -> "点赞改双击确认"
        SettingsItem.COLLECT_CONFIRM -> "收藏改双击确认"
        SettingsItem.TOP_BAR -> "顶栏频道隐藏"
        SettingsItem.BOTTOM_BAR -> "底栏与手势条"
        SettingsItem.SIDE_MENU -> "侧边栏选项隐藏"
        SettingsItem.SIDE_MENU_BG -> "侧边栏背景"
        SettingsItem.SETTINGS_SKIN -> "设置页面换肤"
        SettingsItem.ANTI_RECALL -> "私信防撤回"
        SettingsItem.COMMENT_BG -> "评论区背景美化"
        SettingsItem.COMMENT_TIME -> "评论时间显示"
        SettingsItem.COMMENT_FILTER -> "评论过滤"
        SettingsItem.MARK_ALL_READ -> "一键已读"
        SettingsItem.NOTICE_CLEAN -> "消息与通知净化"
        SettingsItem.ABOUT -> "关于与维护"
    }

    fun desc(item: SettingsItem): String = when (item) {
        SettingsItem.VIDEO_FILTER -> "按互动阈值筛选内容，短剧可独立直滤"
        SettingsItem.VIDEO_AD_BLOCK -> "进入播放器前过滤 Feed 中的广告条目；刷广告赚金币时自动避让"
        SettingsItem.POPUP_AD_BLOCK -> "打开后选择要屏蔽的弹窗；权限申请与版本更新不受影响"
        SettingsItem.CAPTION_OVERLAY -> "在播放页显示发布时间、发布地、播放进度"
        SettingsItem.DOUBLE_TAP_LIKE -> "双击画面中央不再点赞，避免滑屏误触；不影响右侧爱心"
        SettingsItem.LONG_PRESS_LIKE -> "长按右侧爱心不点赞；已点赞时仍可长按取消"
        SettingsItem.LIKE_CONFIRM -> "右侧爱心需双击才点赞/取消；不影响中央手势"
        SettingsItem.COLLECT_CONFIRM -> "右侧收藏需双击才收藏/取消"
        SettingsItem.TOP_BAR -> "隐藏不需要的顶部频道，完整重启快手生效"
        SettingsItem.BOTTOM_BAR -> "独立控制拍摄按钮、底栏与系统手势条"
        SettingsItem.SIDE_MENU -> "隐藏侧边栏里的分组和选项"
        SettingsItem.SIDE_MENU_BG -> "改侧边栏底色 / 背景图与透明度；完整重启快手生效"
        SettingsItem.SETTINGS_SKIN -> "对快手设置页应用主题色，重新进入设置页生效"
        SettingsItem.ANTI_RECALL -> "对方撤回的私信消息保留可见（仅对方消息，本人消息不受影响）；重启快手生效"
        SettingsItem.COMMENT_BG -> "把评论 / 标签半屏面板背景换成深色纯色或自定义图片"
        SettingsItem.COMMENT_TIME -> "把「3 分钟前」换成具体的发布日期时间"
        SettingsItem.COMMENT_FILTER -> "按关键词、长度、IP 属地隐藏评论"
        SettingsItem.MARK_ALL_READ -> "在消息页把所有私信标为已读并清红点"
        SettingsItem.NOTICE_CLEAN -> "清理消息页的好友推荐、通讯录卡与推荐用户"
        SettingsItem.ABOUT -> "版本信息、DexKit 缓存与日志"
    }

    /** 列表右侧的摘要文案（与原版格式一致） */
    fun summary(item: SettingsItem, enabled: Boolean): String = when (item) {
        SettingsItem.VIDEO_FILTER -> {
            val drama = if (Prefs.getBool(Prefs.K_VF_SHORT_DRAMA)) "短剧直接过滤" else "短剧未过滤"
            val th = if (Prefs.getBool(Prefs.K_VF_THRESHOLD)) "普通阈值已启用" else "普通阈值未启用"
            val fb = if (Prefs.getBool(Prefs.K_VF_FALLBACK)) "兜底开启" else "兜底关闭"
            "$drama · $th · $fb"
        }
        SettingsItem.POPUP_AD_BLOCK -> {
            val n = Prefs.getSet(Prefs.K_POPUP_BLOCKED).size
            if (n == 0) "未选择 · 当前不拦截任何弹窗，点此选择" else "已选 $n 个弹窗 · 点此修改"
        }
        SettingsItem.CAPTION_OVERLAY -> {
            if (!enabled) "已关闭"
            else {
                val parts = buildList {
                    if (Prefs.getBool(Prefs.K_CAP_TIME)) add("发布时间")
                    if (Prefs.getBool(Prefs.K_CAP_LOC)) add("发布地")
                    if (Prefs.getBool(Prefs.K_CAP_PROGRESS)) add("播放进度")
                }
                if (parts.isEmpty()) "已开启（未选择显示项）"
                else parts.joinToString(" / ") + " · 已开启"
            }
        }
        SettingsItem.TOP_BAR -> {
            if (!enabled) "全部显示中"
            else {
                val n = Prefs.getSet(Prefs.K_TOP_BAR_TABS).size
                if (n == 0) "已开启 · 暂未选择隐藏项" else "已隐藏 $n 个频道 · 重启生效"
            }
        }
        SettingsItem.BOTTOM_BAR -> buildList {
            if (Prefs.getBool(Prefs.K_BOTTOM_SHOOT_DOUBLE)) add("加号双击打开")
            if (Prefs.getBool(Prefs.K_BOTTOM_SHOOT_HIDDEN)) add("已隐藏加号")
            if (Prefs.getBool(Prefs.K_BOTTOM_BAR_ALL_HIDDEN)) add("隐藏整个底栏")
            if (Prefs.getBool(Prefs.K_GESTURE_BAR_HIDDEN)) add("已隐藏手势条")
        }.let { if (it.isEmpty()) "保持快手原样" else it.joinToString(" · ") + " · 重启生效" }
        SettingsItem.SIDE_MENU -> {
            if (!enabled) "未开启"
            else {
                val g = Prefs.getSet(Prefs.K_SIDE_GROUPS).size
                val i = Prefs.getSet(Prefs.K_SIDE_ITEMS).size
                if (g + i == 0) "已开启 · 暂未选择隐藏项" else "已隐藏 $g 个分组 · $i 个选项 · 重启生效"
            }
        }
        SettingsItem.SIDE_MENU_BG -> {
            if (!enabled) "未开启"
            else if (Prefs.getInt(Prefs.K_SIDE_BG_MODE) == 1)
                if (Prefs.getString(Prefs.K_SIDE_BG_IMAGE).isNotEmpty()) "自定义图片 · 重启生效"
                else "图片未选 · 暂用纯色"
            else "纯色 · 重启生效"
        }
        SettingsItem.SETTINGS_SKIN -> {
            if (!enabled) "已关闭"
            else "已开启 · 主题色 #%06X".format(Prefs.getInt(Prefs.K_SKIN_COLOR) and 0xFFFFFF)
        }
        SettingsItem.COMMENT_FILTER -> {
            if (!enabled) "已关闭"
            else {
                val n = Prefs.multiLine(Prefs.K_COMMENT_BLACKLIST).size
                val r = Prefs.multiLine(Prefs.K_COMMENT_REGIONS).size
                val parts = buildList {
                    if (n > 0) add("$n 个关键词")
                    if (r > 0) add("$r 个属地")
                    if (Prefs.getBool(Prefs.K_COMMENT_ADS)) add("广告特征")
                    if (Prefs.getInt(Prefs.K_COMMENT_MIN_LEN) > 0) add("最短 ${Prefs.getInt(Prefs.K_COMMENT_MIN_LEN)} 字")
                }
                if (parts.isEmpty()) "已开启 · 未设规则" else parts.joinToString(" · ")
            }
        }
        SettingsItem.MARK_ALL_READ -> if (enabled) "已开启" else "已关闭"
        SettingsItem.COMMENT_BG -> {
            if (!enabled) "已关闭"
            else if (Prefs.getInt(Prefs.K_COMMENT_BG_MODE) == 1)
                if (Prefs.getString(Prefs.K_COMMENT_BG_IMAGE).isNotEmpty()) "已开启 · 自定义图片"
                else "已开启 · 图片未选择（暂用深色）"
            else "已开启 · 深色纯色"
        }
        SettingsItem.NOTICE_CLEAN -> {
            if (!enabled) "未开启"
            else {
                val n = buildList {
                    if (Prefs.getBool(Prefs.K_NOTICE_FRIEND)) add(1)
                    if (Prefs.getBool(Prefs.K_NOTICE_CONTACT)) add(1)
                    if (Prefs.getBool(Prefs.K_NOTICE_PYMK)) add(1)
                    if (Prefs.getBool(Prefs.K_NOTICE_PYMK_USERS)) add(1)
                }.size
                if (n == 0) "已开启 · 暂未选择项目" else "已处理 $n 项 · 重启生效"
            }
        }
        else -> if (enabled) "已开启" else "已关闭"
    }

    val orderedGroups: List<SettingsGroup> = SettingsGroup.entries

    fun itemsOf(group: SettingsGroup, pkg: String): List<SettingsItem> =
        SettingsItem.entries.filter { it.group == group && it.visibleOn(pkg) }
}
