package top.nekora.clone.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import top.nekora.clone.state.Prefs
import top.nekora.clone.util.Reflect
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 媒体下载器（还原原版 bq.java 的核心行为）。
 *
 * 原版关键点：
 *   - UA 池：7 个不同 User-Agent 轮换（避免被 CDN 拒）
 *   - URL 签名参数需剥离：tag= / sign= / signature= / clientcachekey=
 *   - 特殊格式：heif / heic / avif / kvif（快手自有格式）
 *   - 重试状态码：400 / 404 / 410
 *   - 线程池：2 线程
 *   - 保存目录：/kimg/（原版常量）
 *
 * 支持三种媒体：
 *   视频  .mp4
 *   图片  .jpg / .webp
 *   live图 .webp + .mp4（快手实况图由两张组成）
 */
object MediaDownloader {

    private const val TAG = "[Download]"

    private val UA_POOL = listOf(
        "Mozilla/5.0 (Linux; Android 14; 23049RAD8C) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.144 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; 2211133C) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.144 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; 2204129AC) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.144 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; Mobile; rv:109.0) Gecko/121.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 Kwai/12.0.0",
    )

    /** 原版 f3888a：需要从 URL 里剥掉的签名参数 */
    private val STRIP_PARAMS = listOf("tag=", "sign=", "signature=", "clientcachekey=")

    /** 原版 f3885a：需要重试的状态码 */
    private val RETRY_CODES = setOf(400, 404, 410)

    /** 原版 f3886a：特殊图片格式 */
    private val SPECIAL_FORMATS = setOf("heif", "heic", "avif", "kvif")

    /** 原版 f3887a */
    private const val SAVE_DIR = "kimg"

    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)

    data class Result(val ok: Int, val total: Int, val type: String, val dir: String)

    /**
     * 批量下载。
     * @param urls   URL 列表（视频 1 个 / 图片组多个 / live 图 2 个）
     * @param type   类型名，用于提示："视频" / "图片" / "实况图"
     * @param onDone 主线程回调
     */
    fun download(
        ctx: Context,
        urls: List<String>,
        type: String,
        prefix: String,
        onDone: (Result) -> Unit,
    ) {
        if (urls.isEmpty()) {
            onDone(Result(0, 0, type, ""))
            return
        }
        if (!running.compareAndSet(false, true)) {
            onDone(Result(0, urls.size, type, ""))
            return
        }

        pool.execute {
            val dir = ensureDir(ctx)
            var ok = 0
            for ((i, url) in urls.withIndex()) {
                val clean = stripUrl(url)
                if (clean.isEmpty()) continue
                val ext = guessExt(clean, type)
                val name = "${prefix}_${System.currentTimeMillis()}_$i.$ext"
                if (save(ctx, clean, dir, name, ext)) ok++
            }
            running.set(false)
            val result = Result(ok, urls.size, type, dir?.absolutePath ?: "")
            main.post { onDone(result) }
        }
    }

    // ==================== URL 处理 ====================

    /** 原版逻辑：剥掉签名参数 */
    private fun stripUrl(raw: String): String {
        var u = raw.trim()
        if (u.isEmpty()) return ""
        // 去掉 query 里的签名参数
        if (u.contains("?")) {
            val idx = u.indexOf('?')
            val base = u.substring(0, idx)
            val query = u.substring(idx + 1)
            val kept = query.split('&').filter { p ->
                STRIP_PARAMS.none { s -> p.lowercase().startsWith(s) }
            }
            u = if (kept.isEmpty()) base else "$base?" + kept.joinToString("&")
        }
        return u
    }

    /** 按 URL 和类型猜扩展名 */
    private fun guessExt(url: String, type: String): String {
        val lower = url.lowercase()
        for (f in SPECIAL_FORMATS) if (lower.contains(".$f")) return "jpg"  // 统一转 jpg
        if (lower.contains(".mp4") || lower.contains(".m3u8")) return "mp4"
        if (lower.contains(".webp")) return "webp"
        if (lower.contains(".png")) return "png"
        if (lower.contains(".gif")) return "gif"
        return when (type) {
            "视频", "实况图" -> "mp4"
            else -> "jpg"
        }
    }

    // ==================== 下载 ====================

    private fun ensureDir(ctx: Context): File? {
        val base = if (Build.VERSION.SDK_INT >= 29) {
            // Android 10+ 用公共 Pictures 目录
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SAVE_DIR)
        } else {
            File(Environment.getExternalStorageDirectory(), "Download/$SAVE_DIR")
        }
        return runCatching {
            if (!base.exists()) base.mkdirs()
            if (base.canWrite()) base else null
        }.getOrNull()
    }

    private fun save(ctx: Context, url: String, dir: File?, name: String, ext: String): Boolean {
        val data = fetch(url, ext) ?: return false
        // 特殊格式转码
        val finalData = if (ext == "jpg") convertToJpeg(data) ?: data else data
        return writeToMediaStore(ctx, name, finalData, ext) ?: writeToFile(dir, name, finalData)
    }

    /** 带 UA 池和重试的抓取 */
    private fun fetch(url: String, ext: String): ByteArray? {
        for (attempt in 0 until 3) {
            val ua = UA_POOL[attempt % UA_POOL.size]
            val conn = runCatching {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", ua)
                    setRequestProperty("Referer", "https://www.kuaishou.com/")
                    setRequestProperty("Accept", "*/*")
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
            }.getOrNull() ?: continue

            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    return conn.inputStream.use { it.readBytes() }
                }
                if (code !in RETRY_CODES) {
                    top.nekora.clone.util.Log.w("$TAG HTTP $code, 放弃")
                    return null
                }
                top.nekora.clone.util.Log.i("$TAG HTTP $code, 重试 ${attempt + 1}/3")
            } catch (t: Throwable) {
                top.nekora.clone.util.Log.w("$TAG 下载异常: ${t.javaClass.simpleName}")
            } finally {
                runCatching { conn.disconnect() }
            }
        }
        return null
    }

    /** heif/heic/avif 转 jpg */
    private fun convertToJpeg(data: ByteArray): ByteArray? = runCatching {
        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return@runCatching null
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        bmp.recycle()
        out.toByteArray()
    }.getOrNull()

    /** Android 10+ 走 MediaStore，相册可见 */
    private fun writeToMediaStore(ctx: Context, name: String, data: ByteArray, ext: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            val isVideo = ext == "mp4"
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(ext))
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isVideo) "${Environment.DIRECTORY_MOVIES}/$SAVE_DIR"
                    else "${Environment.DIRECTORY_PICTURES}/$SAVE_DIR")
            }
            val uri = ctx.contentResolver.insert(collection, cv) ?: return@runCatching false
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) }
            top.nekora.clone.util.Log.i("$TAG 已保存到相册: $name")
            true
        }.getOrDefault(false)
    }

    /** 兜底：直接写文件 */
    private fun writeToFile(dir: File?, name: String, data: ByteArray): Boolean {
        if (dir == null) return false
        return runCatching {
            FileOutputStream(File(dir, name)).use { it.write(data) }
            top.nekora.clone.util.Log.i("$TAG 已保存到文件: ${dir.absolutePath}/$name")
            true
        }.getOrDefault(false)
    }

    private fun mimeOf(ext: String): String = when (ext) {
        "mp4" -> "video/mp4"
        "webp" -> "image/webp"
        "png" -> "image/png"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    /** 从快照对象提取媒体地址（原版 m2367a 的思路） */
    fun extractUrls(snapshot: Any?): List<String> {
        if (snapshot == null) return emptyList()
        val out = LinkedHashSet<String>()
        // 视频地址字段
        val videoFields = listOf(
            "mVideoUrls", "mH265Urls", "mSdUrls", "mVideoUrl",
            "mMainMvUrls", "mMainUrl", "videoUrls", "playUrl", "url"
        )
        // 图片地址字段
        val imageFields = listOf(
            "mImageUrls", "mImageUrl", "mPicUrls", "mPicUrl",
            "mCoverUrls", "mCoverUrl", "mThumbnailUrl", "urls", "images"
        )

        for (f in videoFields + imageFields) {
            val v = Reflect.getFieldValue(snapshot, f) ?: continue
            when (v) {
                is String -> if (v.startsWith("http")) out.add(v)
                is List<*> -> v.forEach { if (it is String && it.startsWith("http")) out.add(it) }
            }
        }
        return out.toList()
    }
}
