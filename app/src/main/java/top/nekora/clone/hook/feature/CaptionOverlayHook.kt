package top.nekora.clone.hook.feature

import android.view.View
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 视频信息 Overlay：在播放页显示发布时间 / 发布地 / 播放进度。
 *
 * 做法：hook 播放页的信息栏 TextView，把三项信息拼进去。
 * 不新增 View（避免破坏布局），复用宿主已有的文本控件。
 */
object CaptionOverlayHook {

    private const val TAG = "[CaptionOverlay]"

    /** 播放页信息栏类候选 */
    private val INFO_VIEW_CLASSES = listOf(
        "com.kwai.slide.play.detail.common.information.caption.CaptionView",
        "com.kwai.kmp.common.elements.information.caption.KmpSlideCaptionElement",
        "com.yxcorp.gifshow.widget.EmojiTextView",
    )

    private val TEXT_FIELDS = listOf("text", "mText", "content", "caption")

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        var hooked = 0
        for (name in INFO_VIEW_CLASSES) {
            val cls = Reflect.findClass(loader, name) ?: continue
            if (hookSetText(module, cls)) hooked++
        }
        if (hooked > 0) Log.hooked("$TAG install pkg=$pkg")
        else Log.w("$TAG no caption view hooked")
    }

    private fun hookSetText(module: XposedModule, cls: Class<*>): Boolean {
        var count = 0
        val allMethods = ArrayList<java.lang.reflect.Method>()
        var c: Class<*>? = cls
        while (c != null && c != Any::class.java) {
            allMethods.addAll(c.declaredMethods)
            c = c.superclass
        }
        for (m in allMethods) {
            if (m.parameterTypes.size != 1) continue
            if (m.parameterTypes[0] != CharSequence::class.java && m.parameterTypes[0] != String::class.java) continue
            val n = m.name.lowercase()
            if (!n.contains("settext") && !n.contains("setcaption") && !n.contains("bind")) continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    if (!Prefs.isEnabled(SettingsItem.CAPTION_OVERLAY)) return@intercept chain.proceed()
                    val original = chain.getArg(0) as? CharSequence ?: return@intercept chain.proceed()
                    val extra = buildExtra()
                    if (extra.isEmpty()) {
                        chain.proceed()
                    } else {
                        val merged = if (original.isBlank()) extra else "$original\n$extra"
                        chain.proceed(arrayOf<Any?>(merged))
                    }
                }
                count++
            }.onFailure { Log.w("$TAG hook ${m.name} failed: ${it.message}") }
        }
        if (count > 0) Log.i("$TAG ${cls.simpleName}: $count setText hooked")
        return count > 0
    }

    private fun buildExtra(): String {
        val parts = ArrayList<String>(3)
        if (Prefs.getBool(Prefs.K_CAP_TIME)) {
            val t = Prefs.getString("caption_cache_time")
            if (t.isNotEmpty()) parts.add("发布时间: $t")
        }
        if (Prefs.getBool(Prefs.K_CAP_LOC)) {
            val loc = Prefs.getString("caption_cache_location")
            if (loc.isNotEmpty()) parts.add("发布地: $loc")
        }
        if (Prefs.getBool(Prefs.K_CAP_PROGRESS)) {
            val p = Prefs.getString("caption_cache_progress")
            parts.add(if (p.isNotEmpty()) p else "--/--")
        }
        return parts.joinToString("  ")
    }
}
