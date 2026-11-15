package top.nekora.clone.hook.feature

import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 弹窗广告拦截。
 *
 * 快手弹窗统一走 com.kwai.library.widget.popup.common.Popup。
 * hook show / showAtLocation，若弹窗 id 在屏蔽名单里就直接拦掉。
 *
 * 权限申请、版本更新、登录、支付类弹窗一律放行（原版规则）。
 */
object PopupBlockHook {

    private const val TAG = "[PopupBlock]"

    private val POPUP_CLASSES = listOf(
        "com.kwai.library.widget.popup.common.Popup",
        "com.yxcorp.gifshow.widget.popup.KwaiPopup",
    )

    /** 永远放行的关键词 */
    private val WHITELIST = listOf(
        "permission", "update", "login", "pay", "coin", "realname", "verify",
    )

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        var hooked = 0
        for (name in POPUP_CLASSES) {
            val cls = Reflect.findClass(loader, name) ?: continue
            if (hookShow(module, cls)) hooked++
        }
        if (hooked > 0) Log.hooked("$TAG install pkg=$pkg")
        else Log.w("$TAG no popup class hooked")
    }

    private fun hookShow(module: XposedModule, cls: Class<*>): Boolean {
        var count = 0
        // 递归所有父类，show 通常在父类里
        val allMethods = ArrayList<java.lang.reflect.Method>()
        var c: Class<*>? = cls
        while (c != null && c != Any::class.java) {
            allMethods.addAll(c.declaredMethods)
            c = c.superclass
        }
        for (m in allMethods) {
            val mn = m.name.lowercase()
            if (!mn.contains("show") && !mn.contains("display") && !mn.contains("present")) continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    if (!Prefs.isEnabled(SettingsItem.POPUP_AD_BLOCK)) return@intercept chain.proceed()
                    val popup = chain.thisObject
                    val id = popupIdOf(popup)
                    if (id != null) {
                        Prefs.setSet(
                            Prefs.K_POPUP_SEEN,
                            Prefs.getSet(Prefs.K_POPUP_SEEN) + id
                        )
                        if (shouldBlock(id)) {
                            Log.i("$TAG blocked popup id=$id")
                            return@intercept null
                        }
                    }
                    chain.proceed()
                }
                count++
            }.onFailure { Log.w("$TAG hook ${m.name} failed: ${it.message}") }
        }
        if (count > 0) Log.i("$TAG ${cls.simpleName}: $count show method(s) hooked")
        return count > 0
    }

    private fun shouldBlock(id: String): Boolean {
        val lower = id.lowercase()
        if (WHITELIST.any { lower.contains(it) }) {
            Log.i("$TAG popup $id in whitelist, allow")
            return false
        }
        val blocked = Prefs.getSet(Prefs.K_POPUP_BLOCKED)
        if (blocked.isEmpty()) return false   // 未选择 = 不拦截任何弹窗
        return blocked.any { it.equals(id, true) }
    }

    /** 尝试从弹窗对象上读出它的标识 */
    private fun popupIdOf(popup: Any?): String? {
        if (popup == null) return null
        for (name in arrayOf("id", "mId", "popupId", "tag", "name", "scene", "mScene")) {
            val v = Reflect.getFieldValue(popup, name)
            if (v is String && v.isNotBlank()) return v
        }
        return null
    }
}
