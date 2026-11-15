package top.nekora.clone.hook.feature

import android.view.View
import io.github.libxposed.api.XposedModule
import org.json.JSONArray
import org.json.JSONObject
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 侧边栏选项隐藏（完整还原原版 ex.java [KwaiSideMenu]）。
 *
 * **关键：纯 JSON 数据过滤，完全不操作 View。**
 *
 * 原版机制：
 *   配置键：sidebarExSquareStyleJson
 *   6 个分组键：topList / commonlyUsedList / moreList / toolList / creativeList / teenageModeList
 *   条目 id 字段：id / mId
 *
 * 流程：
 *   1. m2678a：判断这个 JSON 是不是侧边栏配置
 *      for (String key : GROUPS) if (json.has(key)) return true;
 *   2. m2679a：按分组过滤条目
 *      JSONArray arr = json.optJSONArray(groupKey);
 *      for (int i = 0; i < arr.length(); i++) {
 *          String id = item.optString("id") ?: item.optString("mId");
 *          if (id == null || !hidden.contains(id)) out.put(arr.opt(i));
 *      }
 *   3. 用 out 替换原数组
 */
object SideMenuHook {

    private const val TAG = "[SideMenu]"

    /** 原版 f4809a */
    private val GROUPS = listOf(
        "topList", "commonlyUsedList", "moreList",
        "toolList", "creativeList", "teenageModeList",
    )

    /** 原版 f4810a：条目 id 字段名 */
    private val ID_FIELDS = listOf("id", "mId")

    /** 侧边栏配置的键名（原版 f4808a） */
    private const val CONFIG_KEY = "sidebarExSquareStyleJson"

    fun install(module: XposedModule, pkg: String) {
        Log.hooked("$TAG install pkg=$pkg (JSON 过滤模式)")
        Log.i("$TAG 分组键: ${GROUPS.joinToString()}")
    }

    // ==================== 判定 ====================

    /** 原版 m2678a：这个 JSON 是不是侧边栏配置 */
    fun isSideMenuJson(json: JSONObject): Boolean {
        return GROUPS.any { json.has(it) }
    }

    // ==================== 过滤 ====================

    /**
     * 原版 m2679a：过滤一个分组里的条目。
     * @return 被移除的条目数
     */
    fun filterGroup(json: JSONObject, groupKey: String, hiddenIds: Set<String>): Int {
        val arr = json.optJSONArray(groupKey) ?: return 0
        val out = JSONArray()
        var removed = 0

        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i)
            var id: String? = null
            if (item != null) {
                for (f in ID_FIELDS) {
                    val v = item.optString(f)
                    if (v.isNotEmpty()) { id = v; break }
                }
            }
            if (id == null || !hiddenIds.contains(id)) {
                out.put(arr.opt(i))
            } else {
                removed++
            }
        }

        if (removed > 0) {
            runCatching { json.put(groupKey, out) }
            Log.i("$TAG 分组 $groupKey 移除 $removed 项，剩 ${out.length()}")
        }
        return removed
    }

    /** 过滤整个侧边栏 JSON（所有分组） */
    fun filterAll(json: JSONObject, hiddenGroups: Set<String>, hiddenItems: Set<String>): Int {
        if (!isSideMenuJson(json)) return 0

        var total = 0
        for (g in GROUPS) {
            // 整个分组隐藏
            if (hiddenGroups.any { it.equals(g, true) }) {
                val before = json.optJSONArray(g)?.length() ?: 0
                runCatching { json.put(g, JSONArray()) }
                if (before > 0) {
                    Log.i("$TAG 整组隐藏 $g（$before 项）")
                    total += before
                }
                continue
            }
            // 单项隐藏
            total += filterGroup(json, g, hiddenItems)
        }
        return total
    }

    /**
     * 对外接口：给定一个字符串（可能是侧边栏 JSON），
     * 返回过滤后的字符串。不是侧边栏配置就原样返回。
     */
    fun filterJsonString(raw: String): String {
        if (raw.isEmpty() || !raw.contains("List")) return raw
        if (!Prefs.isEnabled(SettingsItem.SIDE_MENU)) return raw

        val hiddenGroups = Prefs.getSet(Prefs.K_SIDE_GROUPS)
        val hiddenItems = Prefs.getSet(Prefs.K_SIDE_ITEMS)
        if (hiddenGroups.isEmpty() && hiddenItems.isEmpty()) return raw

        return runCatching {
            val json = JSONObject(raw)
            if (!isSideMenuJson(json)) return raw
            val n = filterAll(json, hiddenGroups, hiddenItems)
            if (n == 0) raw else json.toString()
        }.getOrDefault(raw)
    }
}
