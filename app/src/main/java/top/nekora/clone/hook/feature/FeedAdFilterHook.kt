package top.nekora.clone.hook.feature

import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * Feed 广告过滤：进入播放器前把广告条目从列表里摘掉。
 *
 * 目标：Feed 响应里的列表字段。Nekora 是 hook PhotoResponse 的 feed 列表，
 * 这里对候选类和字段做两路兜底。
 */
object FeedAdFilterHook {

    private const val TAG = "[FeedAdFilter]"

    private val CANDIDATE_CLASSES = listOf(
        "com.yxcorp.gifshow.feed.response.PhotoResponse",
        "com.yxcorp.gifshow.model.response.feed.HomeFeedResponse",
        "com.yxcorp.gifshow.network.feed.RecommendFeedResponse",
    )

    /** 判断广告的字段名候选 */
    private val AD_FLAGS = listOf(
        "isAd", "mIsAd", "ad", "mAd", "isAdvertisement",
        "adType", "mAdType", "isCommercial", "commercial",
    )

    /** 值为这些字符串时也认为是广告 */
    private val AD_VALUES = listOf("ad", "ads", "advert", "commercial", "shopping")

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        if (!Prefs.feedAdFilterEnabled) {
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
            val name = m.name.lowercase()
            // 只挂 feed / photo / list 这类语义的 getter，避免误伤
            if (name !in listOf("getfeeds", "getphotos", "getitems", "getlist", "getdata")) continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain -> filter(chain.proceed()) }
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

        val kept = ArrayList<Any?>(list.size)
        var dropped = 0
        for (item in list) {
            if (isAd(item)) {
                dropped++
                continue
            }
            kept.add(item)
        }
        if (dropped > 0) Log.i("$TAG dropped $dropped ad item(s)")
        // 全被过滤时保留原样，避免页面空白
        return if (kept.isEmpty()) raw else kept
    }

    private fun isAd(item: Any?): Boolean {
        if (item == null) return false
        for (flag in AD_FLAGS) {
            val v = Reflect.getFieldValue(item, flag) ?: continue
            when (v) {
                is Boolean -> if (v) return true
                is Int -> if (v == 1) return true
                is String -> if (v.lowercase() in AD_VALUES) return true
            }
        }
        return false
    }
}
