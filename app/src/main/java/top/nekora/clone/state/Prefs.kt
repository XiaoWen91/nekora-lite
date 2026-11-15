package top.nekora.clone.state

import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.api.XposedModule
import top.nekora.clone.util.Log

/**
 * 设置仓库。
 *
 * 跨进程读写靠 libxposed 的 getRemotePreferences：
 *  - 宿主进程内：module.getRemotePreferences(FILE) 读模块 App 写入的值
 *  - 模块 App 内：普通 getSharedPreferences(FILE) 写
 * 框架负责两个进程之间的同步。
 */
object Prefs {

    private const val FILE = "nekora_lite_prefs"

    private var sp: SharedPreferences? = null
    private var module: XposedModule? = null
    private var loaded = false

    /** 宿主进程内调用 */
    fun init(m: XposedModule, packageName: String) {
        module = m
        Log.bind(m)
        if (loaded) return
        loaded = true
        runCatching {
            sp = m.getRemotePreferences(FILE)
            Log.i("prefs bound via getRemotePreferences, pkg=$packageName")
        }.onFailure {
            Log.w("remote prefs unavailable: ${it.javaClass.simpleName}: ${it.message}")
            // 降级：借用宿主自己的 SharedPreferences（同 uid 可读模块私有目录的方案不可靠，
            // 这里只做内存级默认值，避免崩溃）
            sp = null
        }
    }

    /** 模块 App 内调用 */
    fun initInApp(ctx: Context) {
        if (sp != null) return
        sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    private fun s(): SharedPreferences? = sp

    // ---------- 顶栏 ----------
    var topBarHideEnabled: Boolean
        get() = s()?.getBoolean(K_TOP_BAR_ENABLED, false) ?: false
        set(v) = put { it.putBoolean(K_TOP_BAR_ENABLED, v) }

    var hiddenTopBarTabs: Set<String>
        get() = s()?.getStringSet(K_TOP_BAR_TABS, emptySet()) ?: emptySet()
        set(v) = put { it.putStringSet(K_TOP_BAR_TABS, v) }

    // ---------- 底栏 ----------
    var bottomBarHideShoot: Boolean
        get() = s()?.getBoolean(K_BOTTOM_SHOOT_HIDDEN, false) ?: false
        set(v) = put { it.putBoolean(K_BOTTOM_SHOOT_HIDDEN, v) }

    var bottomBarShootDoubleTap: Boolean
        get() = s()?.getBoolean(K_BOTTOM_SHOOT_DOUBLE, false) ?: false
        set(v) = put { it.putBoolean(K_BOTTOM_SHOOT_DOUBLE, v) }

    var bottomBarHidden: Boolean
        get() = s()?.getBoolean(K_BOTTOM_BAR_HIDDEN, false) ?: false
        set(v) = put { it.putBoolean(K_BOTTOM_BAR_HIDDEN, v) }

    // ---------- 侧边栏 ----------
    var sideMenuHideEnabled: Boolean
        get() = s()?.getBoolean(K_SIDE_HIDE, false) ?: false
        set(v) = put { it.putBoolean(K_SIDE_HIDE, v) }

    var hiddenSideMenuGroups: Set<String>
        get() = s()?.getStringSet(K_SIDE_GROUPS, emptySet()) ?: emptySet()
        set(v) = put { it.putStringSet(K_SIDE_GROUPS, v) }

    var hiddenSideMenuItems: Set<String>
        get() = s()?.getStringSet(K_SIDE_ITEMS, emptySet()) ?: emptySet()
        set(v) = put { it.putStringSet(K_SIDE_ITEMS, v) }

    // ---------- 评论过滤 ----------
    var commentFilterEnabled: Boolean
        get() = s()?.getBoolean(K_COMMENT_FILTER, false) ?: false
        set(v) = put { it.putBoolean(K_COMMENT_FILTER, v) }

    var commentBlacklist: List<String>
        get() = lines(K_COMMENT_BLACK)
        set(v) = put { it.putString(K_COMMENT_BLACK, v.joinToString("\n")) }

    var commentMinLen: Int
        get() = s()?.getInt(K_COMMENT_MIN_LEN, 0) ?: 0
        set(v) = put { it.putInt(K_COMMENT_MIN_LEN, v) }

    var commentBlockRegions: List<String>
        get() = lines(K_COMMENT_REGIONS)
        set(v) = put { it.putString(K_COMMENT_REGIONS, v.joinToString("\n")) }

    var commentBlockAds: Boolean
        get() = s()?.getBoolean(K_COMMENT_ADS, false) ?: false
        set(v) = put { it.putBoolean(K_COMMENT_ADS, v) }

    // ---------- Feed 广告过滤 ----------
    var feedAdFilterEnabled: Boolean
        get() = s()?.getBoolean(K_FEED_AD, false) ?: false
        set(v) = put { it.putBoolean(K_FEED_AD, v) }

    // ---------- 私信防撤回 ----------
    var antiRecallEnabled: Boolean
        get() = s()?.getBoolean(K_ANTI_RECALL, false) ?: false
        set(v) = put { it.putBoolean(K_ANTI_RECALL, v) }

    // ---------- 视频信息浮层 ----------
    var captionOverlayEnabled: Boolean
        get() = s()?.getBoolean(K_CAPTION, false) ?: false
        set(v) = put { it.putBoolean(K_CAPTION, v) }

    // ---------- 换肤 ----------
    var skinEnabled: Boolean
        get() = s()?.getBoolean(K_SKIN, false) ?: false
        set(v) = put { it.putBoolean(K_SKIN, v) }

    var skinColor: Int
        get() = s()?.getInt(K_SKIN_COLOR, DEFAULT_SKIN) ?: DEFAULT_SKIN
        set(v) = put { it.putInt(K_SKIN_COLOR, v) }

    // ---------- 内部 ----------
    private inline fun put(block: (SharedPreferences.Editor) -> Unit) {
        val p = s() ?: return
        val e = p.edit()
        block(e)
        e.apply()
    }

    private fun lines(key: String): List<String> =
        s()?.getString(key, "")
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    const val DEFAULT_SKIN = 0xFFFF2D55.toInt()

    private const val K_TOP_BAR_ENABLED = "hidden_top_bar_enabled"
    private const val K_TOP_BAR_TABS = "hidden_top_bar_tabs"
    private const val K_BOTTOM_SHOOT_HIDDEN = "bottom_bar_shoot_hidden"
    private const val K_BOTTOM_SHOOT_DOUBLE = "bottom_bar_shoot_double_tap"
    private const val K_BOTTOM_BAR_HIDDEN = "bottom_bar_hidden"
    private const val K_SIDE_HIDE = "side_menu_hide_enabled"
    private const val K_SIDE_GROUPS = "hidden_side_menu_groups"
    private const val K_SIDE_ITEMS = "hidden_side_menu_items"
    private const val K_COMMENT_FILTER = "comment_filter_enabled"
    private const val K_COMMENT_BLACK = "comment_filter_blacklist"
    private const val K_COMMENT_MIN_LEN = "comment_filter_min_len"
    private const val K_COMMENT_REGIONS = "comment_filter_regions"
    private const val K_COMMENT_ADS = "comment_filter_block_ads"
    private const val K_FEED_AD = "kwai_ad_block_enabled"
    private const val K_ANTI_RECALL = "chat_anti_recall_enabled"
    private const val K_CAPTION = "caption_overlay_enabled"
    private const val K_SKIN = "settings_skin_enabled"
    private const val K_SKIN_COLOR = "settings_skin_color"
}
