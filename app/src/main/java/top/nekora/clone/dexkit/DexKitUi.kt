package top.nekora.clone.dexkit

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import top.nekora.clone.util.Log

/**
 * DexKit 查找进度可视化。
 *
 * 还原原版的两段式交互：
 *   1. 进入面板先弹窗：「当前版本需要查找符号 / 本机查找 / 开始」
 *   2. 点按钮 → 屏幕最顶部出现一条通知样式的进度条
 *      显示：当前阶段 / 已用时间 / 总进度 x/5
 *
 * 进度条直接叠在 decorView 顶部，不依赖宿主任何布局。
 */
object DexKitUi {

    private const val TAG = "[DexKitUi]"

    private val main = Handler(Looper.getMainLooper())

    /** 当前进度条视图 */
    private var bar: View? = null
    private var barText: TextView? = null
    private var barStep: TextView? = null

    // ==================== 进度条 ====================

    /**
     * 在屏幕顶部显示/更新进度条。
     * @param title 当前阶段，如「标准版顶栏数据」
     * @param detail 细节，如「已用 1,177 ms」
     * @param current 当前步骤
     * @param total 总步骤
     */
    fun showProgress(activity: Activity, title: String, detail: String = "", current: Int = 0, total: Int = 5) {
        main.post {
            runCatching {
                val decor = activity.window?.decorView as? ViewGroup ?: return@post

                if (bar == null || bar?.parent == null) {
                    bar = buildBar(activity, decor)
                    decor.addView(bar, buildParams(activity, decor))
                    Log.i("$TAG progress bar attached")
                }
                barText?.text = title
                barStep?.text = if (total > 0) "$current/$total" else detail
                bar?.visibility = View.VISIBLE
            }.onFailure { Log.w("$TAG showProgress failed: ${it.message}") }
        }
    }

    fun updateProgress(title: String, current: Int, total: Int = 5) {
        main.post {
            barText?.text = title
            barStep?.text = "$current/$total"
        }
    }

    fun hideProgress() {
        main.post {
            runCatching {
                (bar?.parent as? ViewGroup)?.removeView(bar)
                bar = null
                barText = null
                barStep = null
                Log.i("$TAG progress bar removed")
            }
        }
    }

    private fun buildBar(activity: Activity, decor: ViewGroup): View {
        val ctx = activity
        val d = ctx.resources.displayMetrics.density
        val dp = { n: Int -> (n * d).toInt() }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xF2FFFFFF.toInt())
                setStroke(dp(1), 0x1A000000)
            }
            elevation = dp(6).toFloat()
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        // 第一行：标题 + 进度
        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        barText = TextView(ctx).apply {
            text = "准备查找"
            textSize = 13f
            setTextColor(0xFF1A1A1A.toInt())
        }
        barStep = TextView(ctx).apply {
            text = "0/5"
            textSize = 11f
            setTextColor(0xFF8A8A8E.toInt())
        }
        row1.addView(barText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row1.addView(barStep)
        root.addView(row1)

        // 第二行：副标题
        root.addView(TextView(ctx).apply {
            text = "总功能进度"
            textSize = 11f
            setTextColor(0xFF9E9E9E.toInt())
        })

        return root
    }

    private fun buildParams(activity: Activity, parent: ViewGroup): ViewGroup.LayoutParams {
        val d = activity.resources.displayMetrics.density
        val dp = { n: Int -> (n * d).toInt() }
        return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            topMargin = dp(8)
            leftMargin = dp(12)
            rightMargin = dp(12)
        }
    }

    // ==================== 查找入口弹窗 ====================

    /**
     * 弹「需要查找符号」对话框。
     * @param onStart 用户点「本机查找」后的回调
     */
    fun showFindDialog(activity: Activity, hasCache: Boolean = false, onStart: () -> Unit) {
        main.post {
            runCatching {
                val builder = android.app.AlertDialog.Builder(activity)
                // 原版 C0221 的文案，分「有缓存 / 无缓存」两种
                if (hasCache) {
                    builder.setTitle("发现可用缓存")
                    builder.setMessage(
                        "当前版本已有查找结果，可下载后复用。\n" +
                            "下载后仍会在本机校验，缺失的结果会自动补充查找。"
                    )
                    builder.setPositiveButton("本机查找") { _, _ ->
                        Log.i("$TAG user clicked 本机查找")
                        onStart()
                    }
                    builder.setNeutralButton("下载缓存") { _, _ ->
                        Log.i("$TAG user clicked 下载缓存")
                        onStart()
                    }
                } else {
                    builder.setTitle("准备查找")
                    builder.setMessage(
                        "当前版本暂无缓存，可以直接在本机查找。\n" +
                            "直接使用前台查找，提示条显示当前功能进度与耗时。"
                    )
                    builder.setPositiveButton("本机查找") { _, _ ->
                        Log.i("$TAG user clicked 本机查找")
                        onStart()
                    }
                }
                builder.setCancelable(false)   // 原版必须处理，不能直接关掉
                builder.show()
                Log.i("$TAG find dialog shown")
            }.onFailure { Log.w("$TAG dialog failed: ${it.message}") }
        }
    }

    /**
     * 带进度的完整查找流程。
     * 在后台线程跑 DexKit，主线程更新进度条。
     */
    fun runFindWithProgress(
        activity: Activity,
        steps: List<Pair<String, () -> Unit>>,
        onDone: (Boolean) -> Unit,
    ) {
        val total = steps.size
        showProgress(activity, "准备查找", "", 0, total)

        Thread {
            val start = System.currentTimeMillis()
            var ok = true
            for ((i, step) in steps.withIndex()) {
                val (name, action) = step
                main.post { updateProgress(name, i + 1, total) }
                runCatching { action() }.onFailure {
                    Log.w("$TAG step '$name' failed: ${it.message}")
                    ok = false
                }
            }
            val cost = System.currentTimeMillis() - start
            Log.i("$TAG find done ok=$ok cost=${cost}ms")

            main.post {
                updateProgress(if (ok) "查找完成 · ${cost} ms" else "部分失败 · ${cost} ms", total, total)
                main.postDelayed({ hideProgress() }, 1200L)
                onDone(ok)
            }
        }.apply { isDaemon = true }.start()
    }
}
