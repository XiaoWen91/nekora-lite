package top.nekora.clone.hook.feature

import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 视频筛选：按互动阈值过滤 Feed。
 *
 * 规则（对应设置面板）：
 *   - 普通视频 / 图文：点赞数、评论数、收藏数 同时满足所有 >0 的阈值
 *   - 短剧：独立开关，不参与阈值
 *   - 兜底：全部不达标时保留最多 3 条最接近的
 */
object VideoFilterHook {

    private const val TAG = "[VideoFilter]"

    private val RESPONSE_CLASSES = listOf(
        "com.yxcorp.gifshow.feed.response.PhotoResponse",
        "com.yxcorp.gifshow.model.response.feed.HomeFeedResponse",
        "com.yxcorp.gifshow.network.feed.RecommendFeedResponse",
    )

    /** 统计数据字段候选 */
    private val LIKE_FIELDS = listOf("likeCount", "mLikeCount", "diggCount", "realLikeCount")
    private val COMMENT_FIELDS = listOf("commentCount", "mCommentCount", "realCommentCount")
    private val COLLECT_FIELDS = listOf("collectCount", "mCollectCount", "favCount")
    private val TYPE_FIELDS = listOf("type", "mType", "feedType", "itemType")

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        var hooked = 0
        for (name in RESPONSE_CLASSES) {
            val cls = Reflect.findClass(loader, name) ?: continue
            if (hookGetters(module, cls)) hooked++
        }
        if (hooked > 0) Log.hooked("$TAG install pkg=$pkg")
        else Log.w("$TAG no response class hooked")
    }

    private fun hookGetters(module: XposedModule, cls: Class<*>): Boolean {
        var count = 0
        for (m in cls.declaredMethods) {
            if (m.parameterTypes.isNotEmpty()) continue
            if (!List::class.java.isAssignableFrom(m.returnType)) continue
            val n = m.name.lowercase()
            if (n !in listOf("getfeeds", "getphotos", "getitems", "getlist")) continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    if (!Prefs.isEnabled(SettingsItem.VIDEO_FILTER)) return@intercept chain.proceed()
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

        val minLike = Prefs.getInt(Prefs.K_VF_MIN_LIKE)
        val minComment = Prefs.getInt(Prefs.K_VF_MIN_COMMENT)
        val minCollect = Prefs.getInt(Prefs.K_VF_MIN_COLLECT)
        val useThreshold = Prefs.getBool(Prefs.K_VF_THRESHOLD) &&
            (minLike > 0 || minComment > 0 || minCollect > 0)
        val filterVideo = Prefs.getBool(Prefs.K_VF_VIDEO, true)
        val filterImageText = Prefs.getBool(Prefs.K_VF_IMAGE_TEXT, true)
        val filterDrama = Prefs.getBool(Prefs.K_VF_SHORT_DRAMA)
        val fallback = Prefs.getBool(Prefs.K_VF_FALLBACK)

        if (!useThreshold && !filterDrama) return raw

        val kept = ArrayList<Any?>(list.size)
        val nearMiss = ArrayList<Pair<Any?, Int>>()
        var dropped = 0

        for (item in list) {
            if (item == null) { kept.add(item); continue }

            val isDrama = isShortDrama(item)
            if (isDrama && filterDrama) {
                dropped++
                continue
            }
            if (isDrama) { kept.add(item); continue }

            if (!useThreshold) { kept.add(item); continue }

            val isVideo = !isImageText(item)
            val inScope = (isVideo && filterVideo) || (!isVideo && filterImageText)
            if (!inScope) { kept.add(item); continue }

            val score = scoreOf(item, minLike, minComment, minCollect)
            if (score >= 0) {
                kept.add(item)
            } else {
                nearMiss.add(item to -score)
                dropped++
            }
        }

        if (fallback && nearMiss.isNotEmpty()) {
            nearMiss.sortedByDescending { it.second }.take(3).forEach { kept.add(it.first) }
            Log.i("$TAG fallback kept ${minOf(3, nearMiss.size)} near-miss item(s)")
        }

        if (dropped > 0) Log.i("$TAG filtered $dropped item(s)")
        return if (kept.isEmpty()) raw else kept
    }

    /** 全部达标返回 0 或正数；不达标返回负的「差距」 */
    private fun scoreOf(item: Any, minLike: Int, minComment: Int, minCollect: Int): Int {
        var deficit = 0
        if (minLike > 0) deficit += (minLike - countOf(item, LIKE_FIELDS)).coerceAtLeast(0)
        if (minComment > 0) deficit += (minComment - countOf(item, COMMENT_FIELDS)).coerceAtLeast(0)
        if (minCollect > 0) deficit += (minCollect - countOf(item, COLLECT_FIELDS)).coerceAtLeast(0)
        return if (deficit == 0) 0 else -deficit
    }

    private fun countOf(item: Any, fields: List<String>): Int {
        for (f in fields) {
            val v = Reflect.getFieldValue(item, f)
            when (v) {
                is Int -> return v
                is Long -> return v.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                is String -> v.toIntOrNull()?.let { return it }
            }
        }
        return 0
    }

    private fun isImageText(item: Any): Boolean {
        for (f in TYPE_FIELDS) {
            val v = Reflect.getFieldValue(item, f)
            if (v is Int) return v == 2 || v == 3
            if (v is String) return v.contains("image", true) || v.contains("photo", true)
        }
        return false
    }

    private fun isShortDrama(item: Any): Boolean {
        for (name in arrayOf("isShortDrama", "shortDrama", "isDrama", "dramaId")) {
            val v = Reflect.getFieldValue(item, name)
            if (v is Boolean && v) return true
            if (v is Number && v.toInt() > 0) return true
            if (v is String && v.isNotEmpty()) return true
        }
        return false
    }
}
