package top.nekora.clone.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 面板状态：直接读写 Prefs */
class PanelState(private val pkg: String) {

    var userName by mutableStateOf(Prefs.getString(Prefs.K_USER_NAME))
        private set
    var kwaiId by mutableStateOf(Prefs.getString(Prefs.K_USER_KWAI))
        private set
    var userId by mutableStateOf(Prefs.getString(Prefs.K_USER_ID))
        private set

    private val cache = HashMap<SettingsItem, Boolean>().apply {
        SettingsItem.entries.forEach { put(it, Prefs.isEnabled(it)) }
    }

    fun enabled(item: SettingsItem) = cache[item] ?: false

    fun setEnabled(item: SettingsItem, v: Boolean) {
        cache[item] = v
        Prefs.setEnabled(item, v)
    }

    fun favorites(pkg: String): List<SettingsItem> {
        val saved = Prefs.getSet(Prefs.K_FAVORITES)
        val ids = if (saved.isEmpty()) DEFAULT_FAV else saved
        return SettingsItem.entries.filter {
            it.name in ids && it.visibleOn(pkg) && !it.hasDetail
        }
    }

    fun toggleFavorite(item: SettingsItem, on: Boolean) {
        val cur = Prefs.getSet(Prefs.K_FAVORITES).toMutableSet()
        if (on) cur.add(item.name) else cur.remove(item.name)
        Prefs.setSet(Prefs.K_FAVORITES, cur)
    }

    fun refreshUser() {
        userName = Prefs.getString(Prefs.K_USER_NAME)
        kwaiId = Prefs.getString(Prefs.K_USER_KWAI)
        userId = Prefs.getString(Prefs.K_USER_ID)
    }

    /** 每个面板的总开关键；没有就返回 null */
    fun masterKeyOf(panel: Panel): String? = when (panel) {
        Panel.TOP_BAR -> "top_bar.enabled"
        Panel.SIDE_MENU -> "side_menu.enabled"
        Panel.SIDE_MENU_BG -> "side_menu_bg.enabled"
        Panel.NOTICE_CLEAN -> "notice_clean.enabled"
        Panel.SKIN -> "settings_skin.enabled"
        else -> null
    }

    private companion object {
        val DEFAULT_FAV = setOf(
            SettingsItem.DOUBLE_TAP_LIKE.name,
            SettingsItem.ANTI_RECALL.name,
        )
    }
}
