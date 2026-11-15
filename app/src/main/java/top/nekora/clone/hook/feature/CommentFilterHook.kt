package top.nekora.clone.hook.feature

import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 评论过滤。
 *
 * 快手评论区列表在 KmpCommentFeaturePageList 里装配，Nekora 也是从这里下手。
 * 这里 hook 返回 List 的 getter，按规则过滤。
 *
 * 规则（对应设置面板）：
 *   - 关键词黑名单
 *   - 最短长度
 *   - IP 属地屏蔽
 *   - 广告/引流特征
 */
object CommentFilterHook {

    private const val TAG = "[CommentFilter]"

    private val CANDIDATE_CLASSES = listOf(
        "com.kwai.kmp.comment.mvi.panel.pagelist.KmpCommentFeaturePageList",
        "com.kwai.kmp.comment.mvi.panel.pagelist.KmpCommentFeaturePageList\$Companion",
    )

    /** 广告/引流特征词，命中任意一个就隐藏 */
    private val AD_PATTERNS = listOf(
        "加微信", "加V", "加v", "薇信", "微芯", "威信",
        "加群", "进群", "私信我", "私聊我", "vx：", "VX：", "vx:", "VX:",
        "QQ群", "qq群", "扣扣群",
        "http://", "https://", "www.", ".com", ".cn", ".top",
        "代购", "兼职", "刷单", "日赚", "月入", "躺赚", "带单", "导师",
    )

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        if (!Prefs.commentFilterEnabled) {
            Log.i("$TAG disabled, skip")
            return
        }

        var hookedAny = false
        for (name in CANDIDATE_CLASSES) {
            val cls = Reflect.findClass(loader, name) ?: continue
            if (hookListGetters(module, cls)) hookedAny = true
        }

        if (hookedAny) Log.hooked("$TAG install pkg=$pkg")
        else Log.w("$TAG no candidate class hooked")
    }

    private fun hookListGetters(module: XposedModule, cls: Class<*>): Boolean {
        var count = 0
        for (m in cls.declaredMethods) {
            if (m.parameterTypes.isNotEmpty()) continue
            if (!List::class.java.isAssignableFrom(m.returnType)) continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    filter(chain.proceed())
                }
                count++
            }.onFailure { Log.w("$TAG hook ${m.name} failed: ${it.message}") }
        }
        if (count > 0) Log.i("$TAG ${cls.simpleName}: $count getter(s) hooked")
        return count > 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun filter(raw: Any?): Any? {
        val list = raw as? List<Any?> ?: return raw
        if (list.isEmpty()) return raw

        val blacklist = Prefs.commentBlacklist
        val minLen = Prefs.commentMinLen
        val regions = Prefs.commentBlockRegions
        val blockAds = Prefs.commentBlockAds

        val kept = ArrayList<Any?>(list.size)
        for (item in list) {
            val text = textOf(item) ?: run { kept.add(item); continue }

            if (minLen > 0 && text.length < minLen) {
                Log.i("$TAG hide short(${text.length}<$minLen): ${text.take(12)}")
                continue
            }
            if (blacklist.any { text.contains(it, ignoreCase = true) }) {
                Log.i("$TAG hide blacklist: ${text.take(12)}")
                continue
            }
            if (blockAds && AD_PATTERNS.any { text.contains(it, ignoreCase = true) }) {
                Log.i("$TAG hide ad-pattern: ${text.take(12)}")
                continue
            }
            if (regions.isNotEmpty()) {
                val ip = ipOf(item)
                if (ip != null && regions.any { ip.contains(it) }) {
                    Log.i("$TAG hide region=$ip")
                    continue
                }
            }
            kept.add(item)
        }
        return kept
    }

    private fun textOf(item: Any?): String? {
        if (item == null) return null
        if (item is String) return item
        for (name in arrayOf("content", "text", "mContent", "comment", "body", "label")) {
            val v = Reflect.getFieldValue(item, name)
            if (v is String && v.isNotEmpty()) return v
        }
        return null
    }

    private fun ipOf(item: Any?): String? {
        if (item == null) return null
        for (name in arrayOf("ipLocation", "location", "mIpLocation", "area", "region")) {
            val v = Reflect.getFieldValue(item, name)
            if (v is String && v.isNotEmpty()) return v
        }
        return null
    }
}
