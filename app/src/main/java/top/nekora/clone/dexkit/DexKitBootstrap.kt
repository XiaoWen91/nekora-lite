package top.nekora.clone.dexkit

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.github.libxposed.api.XposedModule
import top.nekora.clone.dexkit.DexCache
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect

/**
 * DexKit 首次查找引导。
 *
 * **与原版 ensureShown 一致的时机：进入快手就弹，不等打开面板。**
 *
 * 流程：
 *   1. hook Application.attach 拿 Context
 *   2. hook Activity.onResume，第一个 Activity 出现时触发一次
 *   3. 没缓存 → 弹「准备查找」对话框（单按钮）
 *      有缓存 → 弹「发现可用缓存」对话框（双按钮）
 *   4. 点「本机查找」→ 屏幕顶部横幅显示进度，期间用户可以正常操作
 *   5. 查找完成后写缓存，下次不再弹
 *
 * 原版关键点：**弹窗可关闭不可取消**，且进度条不阻塞操作。
 */
object DexKitBootstrap {

    private const val TAG = "[DexKitBoot]"

    private val main = Handler(Looper.getMainLooper())

    private var appCtx: Context? = null
    private var firstActivity: Activity? = null
    private var prompted = false
    private var running = false

    /**
     * @param onFinished 查找完成后回调（用于重新应用依赖符号的 hook）
     */
    fun install(module: XposedModule, onFinished: (Boolean) -> Unit) {
        hookApplicationAttach(module)
        hookActivityResume(module, onFinished)
        Log.i("$TAG installed")
    }

    private fun hookApplicationAttach(module: XposedModule) {
        val m = runCatching {
            Application::class.java
                .getDeclaredMethod("attach", Context::class.java)
                .apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val r = chain.proceed()
            (chain.getArg(0) as? Context)?.let { appCtx = it }
            r
        }
        Log.i("$TAG hooked Application.attach")
    }

    /**
     * 第一个 Activity onResume 时触发。
     * 只触发一次，且跳过我们自己的 Activity。
     */
    private fun hookActivityResume(module: XposedModule, onFinished: (Boolean) -> Unit) {
        val m = runCatching {
            Activity::class.java.getDeclaredMethod("onResume").apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val r = chain.proceed()
            val act = chain.thisObject as? Activity
            if (act != null && !prompted) {
                val cn = act.javaClass.name
                // 跳过非宿主 Activity
                if (!cn.startsWith("top.nekora.clone")) {
                    prompted = true
                    firstActivity = act
                    main.postDelayed({ maybePrompt(act, onFinished) }, 600L)
                }
            }
            r
        }
        Log.i("$TAG hooked Activity.onResume")
    }

    /** 判断要不要弹窗 */
    private fun maybePrompt(act: Activity, onFinished: (Boolean) -> Unit) {
        if (running) return
        if (act.isFinishing) return

        val ready = DexKitResolver.isReady()
        val cached = DexKitResolver.hasCache()

        Log.i("$TAG maybePrompt ready=$ready cached=$cached")

        // 只要有有效缓存就不弹 —— 缓存存在即代表扫过，无论本次进程有没有建 bridge。
        // 依赖符号时 DexKitResolver.findClass 会先查缓存，不需要 bridge 也能用。
        if (cached) {
            Log.i("$TAG cache valid (host 版本未变)，跳过弹窗，直接使用缓存")
            runCatching { onFinished(true) }
            return
        }

        // 内存里已有 bridge（例如热重载）也不弹
        if (ready) {
            Log.i("$TAG dexkit already ready in-memory，跳过弹窗")
            runCatching { onFinished(true) }
            return
        }

        // 走到这里说明：既无缓存也无 bridge → 首次安装或快手升级过
        Log.i("$TAG 无缓存且未扫描 → 弹「准备查找」")
        DexKitUi.showFindDialog(act, hasCache = false) {
            startScan(act, onFinished)
        }
    }

    /** 开始扫描：顶部横幅 + 后台线程 */
    private fun startScan(act: Activity, onFinished: (Boolean) -> Unit) {
        if (running) return
        running = true

        val steps = buildSteps()
        DexKitUi.runFindWithProgress(act, steps) { ok ->
            running = false
            // DexKit 失败不代表功能不可用 —— 类名直连已经覆盖 20/21
            val effective = ok || DexKitResolver.isReady()
            Log.i("$TAG scan finished dexkitOk=$ok effective=$effective")
            runCatching { onFinished(effective) }
                .onFailure { Log.w("$TAG onFinished failed: ${it.message}") }
        }
    }

    /** 5 个步骤，对应原版进度条上的功能 */
    private fun buildSteps(): List<Pair<String, () -> Unit>> = listOf(
        "加载 DexKit native" to { DexKitResolver.ensureScanned() },
        "标准版顶栏数据" to { runCatching { DexKitResolver.tabModelClass() } },
        "设置页入口" to { runCatching { DexKitResolver.settingsItemListClass() } },
        "底栏动作" to { runCatching { DexKitResolver.bottomBarClass() } },
        "分享面板" to { runCatching { DexKitResolver.sharePanelClass() } },
    )

    /** 供面板「重建 DexKit 查找缓存」手动触发 */
    fun rescan(activity: Activity, onFinished: (Boolean) -> Unit) {
        Log.i("$TAG manual rescan requested")
        runCatching { DexCache.clear() }
        startScan(activity, onFinished)
    }

    /**
     * 面板里「DexKit 缓存」入口调用：
     * 有缓存时显示双按钮对话框（下载缓存 / 本机查找），与原版一致。
     */
    fun showCacheDialog(activity: Activity, onFinished: (Boolean) -> Unit) {
        val cached = DexKitResolver.hasCache()
        DexKitUi.showFindDialog(activity, hasCache = cached) {
            startScan(activity, onFinished)
        }
    }
}
