package top.nekora.clone.hook.feature

import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 侧边栏选项 / 分组隐藏。
 *
 * 快手的侧边栏数据来自配置 JSON（sidebarExSquareStyleJson），
 * 分组键：topList / commonlyUsedList / moreList / toolList / creativeList / teenageModeList
 */
object SideMenuHook {

    private const val TAG = "[SideMenu]"

    private val GROUP_KEYS = listOf(
        "topList", "commonlyUsedList", "moreList",
        "toolList", "creativeList", "teenageModeList",
    )

    private val CANDIDATE_CLASSES = listOf(
        "com.yxcorp.gifshow.sidemenu.SideMenuFragment",
        "com.kwai.kcube.ext.widget.SideMenuLayout",
    )

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        if (!Prefs.sideMenuHideEnabled) {
            Log.i("$TAG disabled, skip")
            return
        }
        Log.i(
            "$TAG enabled groups=${Prefs.hiddenSideMenuGroups} items=${Prefs.hiddenSideMenuItems}"
        )
        // 侧边栏是配置驱动，真正生效点依赖宿主版本的 JSON 解析，
        // 这里给出结构定位与可用接口，具体字段按宿主版本适配。
        for (name in CANDIDATE_CLASSES) {
            val cls = Reflect.findClass(loader, name) ?: continue
            Log.i("$TAG found ${cls.simpleName}")
        }
        Log.hooked("$TAG install pkg=$pkg")
    }

    /** 判断某个分组是否应隐藏 */
    fun shouldHideGroup(key: String): Boolean =
        Prefs.hiddenSideMenuGroups.any { it.equals(key, ignoreCase = true) }

    /** 判断某个侧边栏项是否应隐藏（按 id 或名称） */
    fun shouldHideItem(id: String?, name: String?): Boolean {
        val hidden = Prefs.hiddenSideMenuItems
        if (hidden.isEmpty()) return false
        return hidden.any {
            it.equals(id, ignoreCase = true) || it.equals(name, ignoreCase = true)
        }
    }

    fun groupKeys(): List<String> = GROUP_KEYS
}
