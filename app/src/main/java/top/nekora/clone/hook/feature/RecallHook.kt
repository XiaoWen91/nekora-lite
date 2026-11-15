package top.nekora.clone.hook.feature

import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 私信防撤回：对方撤回后消息仍然可见。
 *
 * 目标类：
 *   com.kwai.imsdk.msg.RecalledMsg
 *   com.yxcorp.gifshow.message.sdk.message.KRecalledMsg
 *
 * 只处理对方消息，自己撤回的不受影响。
 */
object RecallHook {

    private const val TAG = "[AntiRecall]"

    private val CANDIDATE_CLASSES = listOf(
        "com.kwai.imsdk.msg.RecalledMsg",
        "com.yxcorp.gifshow.message.sdk.message.KRecalledMsg",
    )

    /** 撤回标记字段 */
    private val RECALL_FLAGS = listOf(
        "isRecalled", "mIsRecalled", "recalled", "mRecalled",
        "showRecallTip", "mShowRecallTip", "isRevoked", "mIsRevoked",
    )

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        if (!Prefs.antiRecallEnabled) {
            Log.i("$TAG disabled, skip")
            return
        }

        var hooked = false
        for (name in CANDIDATE_CLASSES) {
            val cls = Reflect.findClass(loader, name) ?: continue
            if (hookBooleanGetters(module, cls)) hooked = true
        }

        if (hooked) Log.hooked("$TAG install pkg=$pkg")
        else Log.w("$TAG no candidate class hooked")
    }

    private fun hookBooleanGetters(module: XposedModule, cls: Class<*>): Boolean {
        var count = 0
        for (m in cls.declaredMethods) {
            if (m.parameterTypes.isNotEmpty()) continue
            if (m.returnType != Boolean::class.javaPrimitiveType) continue

            val n = m.name.lowercase()
            val isRecallRelated = RECALL_FLAGS.any { n.contains(it.lowercase()) } ||
                n.contains("recall") || n.contains("revoke")
            if (!isRecallRelated) continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    val original = chain.proceed() as? Boolean ?: false
                    if (original) {
                        Log.i("$TAG force false: ${m.name}")
                        false
                    } else {
                        original
                    }
                }
                count++
            }.onFailure { Log.w("$TAG hook ${m.name} failed: ${it.message}") }
        }
        if (count > 0) Log.i("$TAG ${cls.simpleName}: $count getter(s) hooked")
        return count > 0
    }
}
