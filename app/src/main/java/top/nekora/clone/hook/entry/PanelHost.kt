package top.nekora.clone.hook.entry

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.ConfigStore
import top.nekora.clone.ui.SettingsPanel
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect
import top.nekora.clone.dexkit.DexKitResolver
import top.nekora.clone.dexkit.DexKitUi
import java.lang.ref.WeakReference

/**
 * 在宿主进程里以 Dialog 形式弹出设置面板。
 *
 * 原版是 DialogC0010（一个 Compose Dialog），这里用 android.app.Dialog
 * 承载 ComposeView，效果一致：不跳 Activity、不打断视频播放。
 */
object PanelHost {

    private const val TAG = "[Panel]"

    private var current: Dialog? = null
    private var currentActivity: WeakReference<Activity>? = null
    private val main = Handler(Looper.getMainLooper())

    /** 当前顶层 Activity（原版用 ActivityThread.mActivities 反射拿） */
    fun topActivity(): Activity? {
        currentActivity?.get()?.let { if (!it.isFinishing) return it }

        val found = runCatching {
            val at = Class.forName("android.app.ActivityThread")
            val cur = at.getDeclaredMethod("currentActivityThread").apply { isAccessible = true }
                .invoke(null) ?: return@runCatching null
            val mActivities = at.getDeclaredField("mActivities").apply { isAccessible = true }
                .get(cur) as? Map<*, *> ?: return@runCatching null

            mActivities.values.firstNotNullOfOrNull { record ->
                if (record == null) return@firstNotNullOfOrNull null
                val paused = Reflect.getFieldValue(record, "paused") as? Boolean ?: false
                if (paused) return@firstNotNullOfOrNull null
                Reflect.getFieldValue(record, "activity") as? Activity
            }
        }.getOrNull()

        if (found != null) currentActivity = WeakReference(found)
        return found
    }

    /**
     * 从任意 Context 反推 Activity 并弹面板。
     * 评论区入口拿到的是 View 的 Context（可能是 ContextThemeWrapper）。
     */
    fun showForComment(ctx: Context) {
        val act = runCatching {
            var c: Context? = ctx
            while (c is android.content.ContextWrapper) {
                if (c is Activity) return@runCatching c
                c = c.baseContext
            }
            null
        }.getOrNull() ?: topActivity()

        if (act == null) {
            Log.w("$TAG showForComment: 找不到 Activity")
            return
        }
        showOnActivity(act, currentPkg ?: "com.smile.gifmaker")
    }

    /** 记住最近一次的包名，供无 module 引用时使用 */
    var currentPkg: String? = null
        private set

    /** 在指定 Activity 上弹面板 */
    fun showOnActivity(activity: Activity, pkg: String) {
        main.post {
            if (current?.isShowing == true) return@post
            currentPkg = pkg
            // ★ 关键：面板在宿主进程里渲染，必须用宿主 Context 初始化配置存储，
            // 否则 Prefs 写入全部丢失（这就是配置一直 0 个键的原因）
            runCatching { ConfigStore.initInHost() }
            runCatching {
                val dialog = Dialog(activity)
                val view = SettingsPanel.build(activity, pkg) { dialog.dismiss() }
                dialog.setContentView(view)
                dialog.setOnDismissListener { current = null }
                dialog.show()
                current = dialog
                Log.i("$TAG 面板已弹出 pkg=$pkg")
            }.onFailure { Log.w("$TAG 弹面板失败: ${it.message}") }
        }
    }

    /** 弹出面板；已经在显示时不再重复弹 */
    fun show(module: XposedModule, pkg: String) {
        main.post {
            if (current?.isShowing == true) {
                Log.i("$TAG already showing")
                return@post
            }
            val activity = topActivity()
            if (activity == null || activity.isFinishing) {
                Log.w("$TAG no activity, cannot show panel")
                return@post
            }

            runCatching {
                val dialog = Dialog(activity)
                val view = SettingsPanel.build(activity, pkg) {
                    dialog.dismiss()
                }
                dialog.setContentView(view)
                dialog.setOnDismissListener {
                    current = null
                    Log.i("$TAG dismissed")
                }
                dialog.show()
                current = dialog
                Log.i("$TAG shown pkg=$pkg activity=${activity.javaClass.simpleName}")
            }.onFailure {
                Log.w("$TAG show failed: ${it.javaClass.simpleName}: ${it.message}")
            }
        }
    }

    /** 查找步骤：与原版进度条上的 5 步对应 */
    private fun buildFindSteps(): List<Pair<String, () -> Unit>> = listOf(
        "本机查找" to { DexKitResolver.ensureScanned() },
        "标准版顶栏数据" to { DexKitResolver.tabModelClass() },
        "设置页入口" to { DexKitResolver.settingsItemListClass() },
        "底栏动作" to { DexKitResolver.bottomBarClass() },
        "分享面板" to { DexKitResolver.sharePanelClass() },
    )

    fun dismiss() {
        main.post {
            runCatching { current?.dismiss() }
            current = null
        }
    }
}
