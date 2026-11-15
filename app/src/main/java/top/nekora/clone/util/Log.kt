package top.nekora.clone.util

import android.util.Log as AndroidLog
import io.github.libxposed.api.XposedModule

/**
 * 统一日志：同时写 logcat 与 Xposed 日志（宿主内可在 LSPosed 里看到）。
 */
object Log {

    var tag: String = "NekoraLite"

    private var module: XposedModule? = null

    /** hook 安装计数，用于自检「到底挂上了几个」 */
    var counter: Int = 0
        private set

    fun bind(m: XposedModule) {
        module = m
    }

    fun i(msg: String) {
        AndroidLog.i(tag, msg)
        runCatching { module?.log(4, tag, msg) }
    }

    fun w(msg: String) {
        AndroidLog.w(tag, msg)
        runCatching { module?.log(5, tag, msg) }
    }

    fun e(msg: String, t: Throwable? = null) {
        AndroidLog.e(tag, msg, t)
        runCatching { module?.log(6, tag, msg, t) }
    }

    fun hooked(what: String) {
        counter++
        i("hooked[$counter] $what")
    }
}
