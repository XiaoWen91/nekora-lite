package top.nekora.clone.hook.feature

import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 评论时间显示。
 *
 * 照原版 qk.java [KwaiCommentTime] 的做法：
 *   - 目标类 com.kuaishou.android.model.mix.QComment
 *   - 目标工具 com.yxcorp.gifshow.util.DateUtils
 *   - hook 评论时间格式化，把「3分钟前」换成具体时间
 */
object CommentTimeHook {

    private const val TAG = "[CommentTime]"

    private const val QCOMMENT = "com.kuaishou.android.model.mix.QComment"
    private const val DATE_UTILS = "com.yxcorp.gifshow.util.DateUtils"

    private val TIME_FIELDS = listOf("createTime", "mCreateTime", "time", "timestamp", "publishTime")
    private val FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        val commentCls = Reflect.findClass(loader, QCOMMENT)
        val dateCls = Reflect.findClass(loader, DATE_UTILS)

        var hooked = 0
        if (dateCls != null && hookDateFormatter(module, dateCls)) hooked++
        if (commentCls != null) Log.i("$TAG QComment found")

        if (hooked > 0) Log.hooked("$TAG install pkg=$pkg")
        else Log.w("$TAG DateUtils not found")
    }

    /** 找 DateUtils 里 (Context, long) → String 的格式化方法 */
    private fun hookDateFormatter(module: XposedModule, cls: Class<*>): Boolean {
        var count = 0
        for (m in cls.declaredMethods) {
            if (!java.lang.reflect.Modifier.isStatic(m.modifiers)) continue
            if (m.returnType != String::class.java) continue
            val ps = m.parameterTypes
            val ok = ps.size == 2 &&
                (ps[0] == Long::class.javaPrimitiveType || ps[0] == Long::class.javaObjectType) &&
                ps[1] == Long::class.javaPrimitiveType
            val ok2 = ps.size == 2 &&
                android.content.Context::class.java.isAssignableFrom(ps[0]) &&
                (ps[1] == Long::class.javaPrimitiveType || ps[1] == Long::class.javaObjectType)
            if (!ok && !ok2) continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    val args = chain.args
                    val ts = args.firstOrNull {
                        it is Long && it > 1_000_000_000_000L
                    } as? Long
                    val original = chain.proceed() as? String ?: ""
                    if (ts != null && looksRelative(original)) {
                        val exact = FMT.format(Date(ts))
                        Log.i("$TAG time '$original' -> '$exact'")
                        exact
                    } else {
                        original
                    }
                }
                count++
            }.onFailure { Log.w("$TAG hook ${m.name} failed: ${it.message}") }
        }
        if (count > 0) Log.i("$TAG DateUtils: $count formatter hooked")
        return count > 0
    }

    private fun looksRelative(s: String): Boolean =
        s.contains("前") || s.contains("刚刚") || s.contains("ago")
}
