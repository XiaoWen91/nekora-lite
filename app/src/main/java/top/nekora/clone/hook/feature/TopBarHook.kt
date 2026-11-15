package top.nekora.clone.hook.feature

import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 顶栏频道隐藏。
 *
 * 快手顶栏数据来自 kcube 的 TabModel / TabConfig，Nekora 用 DexKit 定位，
 * 这里先用类名直连 + 字段类型兜底两条路，可编译可跑。
 *
 * 目标类：
 *   com.kwai.component.kcube.model.model.TabModel
 *   com.kwai.component.kcube.model.model.TabConfig
 *   com.kuaishou.gifshow.platform.network.keyconfig.DynamicTabConfig
 *
 * 生效方式：hook 列表 getter，返回前按配置过滤；需重启宿主。
 */
object TopBarHook {

    private const val TAG = "[TopBar]"

    private val CANDIDATE_CLASSES = listOf(
        "com.kwai.component.kcube.model.model.TabModel",
        "com.kwai.component.kcube.model.model.TabConfig",
        "com.kuaishou.gifshow.platform.network.keyconfig.DynamicTabConfig",
    )

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        if (!Prefs.topBarHideEnabled) {
            Log.i("$TAG disabled, skip")
            return
        }

        var hookedAny = false
        for (name in CANDIDATE_CLASSES) {
            val cls = Reflect.findClass(loader, name) ?: continue
            if (hookListGetters(module, cls)) hookedAny = true
        }

        if (hookedAny) {
            Log.hooked("$TAG install pkg=$pkg hidden=${Prefs.hiddenTopBarTabs}")
        } else {
            Log.w("$TAG no candidate class hooked (宿主版本不匹配时部分 hook 未命中)")
        }
    }

    /** 给类里所有返回 List 的 getter 挂过滤 */
    private fun hookListGetters(module: XposedModule, cls: Class<*>): Boolean {
        var count = 0
        for (m in cls.declaredMethods) {
            if (m.parameterTypes.isNotEmpty()) continue
            if (!List::class.java.isAssignableFrom(m.returnType)) continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    val raw = chain.proceed()
                    filter(chain.thisObject, raw)
                }
                count++
            }.onFailure {
                Log.w("$TAG hook ${cls.simpleName}.${m.name} failed: ${it.message}")
            }
        }
        if (count > 0) Log.i("$TAG ${cls.simpleName}: $count list getter(s) hooked")
        return count > 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun filter(owner: Any?, raw: Any?): Any? {
        val list = raw as? List<Any?> ?: return raw
        if (list.isEmpty()) return raw

        val hidden = Prefs.hiddenTopBarTabs
        if (hidden.isEmpty()) return raw

        val kept = ArrayList<Any?>(list.size)
        for (item in list) {
            val id = tabIdOf(item)
            if (id != null && hidden.any { it.equals(id, ignoreCase = true) }) {
                Log.i("$TAG hide tab id=$id")
                continue
            }
            kept.add(item)
        }

        // 全部被隐藏时保留原样，避免顶栏空白导致宿主异常
        return if (kept.isEmpty()) raw else kept
    }

    /** 依次尝试常见字段名，取频道标识 */
    private fun tabIdOf(item: Any?): String? {
        if (item == null) return null
        for (name in arrayOf("id", "mId", "tabId", "mTabId", "name", "mName", "type")) {
            val v = Reflect.getFieldValue(item, name)
            if (v is String && v.isNotBlank()) return v
        }
        return null
    }
}
