package top.nekora.clone.hook.feature

import android.app.Activity
import android.graphics.Color
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * 设置页面换肤。
 *
 * 目标：com.yxcorp.plugin.setting.activity.SettingsActivity
 * 做法：hook onResume，改状态栏 / 导航栏 / 标题栏颜色。
 */
object SettingsSkinHook {

    private const val TAG = "[Skin]"

    private val SETTINGS_ACTIVITY = listOf(
        "com.yxcorp.plugin.setting.activity.SettingsActivity",
        "com.yxcorp.gifshow.settings.page.SettingActivity",
    )

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        var hooked = 0
        for (name in SETTINGS_ACTIVITY) {
            val cls = Reflect.findClass(loader, name) ?: continue
            val m = Reflect.findMethodInHierarchy(cls, "onResume") ?: continue
            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    val r = chain.proceed()
                    (chain.thisObject as? Activity)?.let { applySkin(it) }
                    r
                }
                hooked++
                Log.hooked("$TAG ${cls.simpleName}.onResume")
            }.onFailure { Log.w("$TAG hook failed: ${it.message}") }
        }
        if (hooked == 0) Log.w("$TAG settings activity not found pkg=$pkg")
    }

    private fun applySkin(act: Activity) {
        if (!Prefs.isEnabled(SettingsItem.SETTINGS_SKIN)) return
        val color = Prefs.getInt(Prefs.K_SKIN_COLOR, Prefs.DEFAULT_SKIN)
        runCatching {
            act.window?.apply {
                statusBarColor = color
                navigationBarColor = color
                decorView.setBackgroundColor(color)
            }
            Log.i("$TAG skin applied #%06X".format(color and 0xFFFFFF))
        }.onFailure { Log.w("$TAG apply failed: ${it.message}") }
    }
}
