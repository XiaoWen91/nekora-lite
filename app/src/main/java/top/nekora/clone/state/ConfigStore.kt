package top.nekora.clone.state

import android.content.Context
import top.nekora.clone.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * 配置存储。
 *
 * **这是本项目最关键的修正点。**
 *
 * 原版（C0062/s60/g3）不用 SharedPreferences 默认目录，而是把配置写在外部存储：
 *     /storage/emulated/0/Download/Mayi_Tool/config/
 *
 * 原因：模块 App 进程和快手宿主进程**都能读写同一个物理文件**，
 * 完全绕开跨进程 SharedPreferences 的同步问题。
 *
 * 此前的实现用 libxposed 的 getRemotePreferences，依赖框架实现，实测不可靠，
 * 导致「模块 App 里改了开关，宿主进程读到的还是默认值」——功能永远不生效。
 *
 * 本实现用最简单的 Properties 格式（key=value），两端直接读写同一个文件，
 * 每次读取都 stat 一次 mtime，变了就重载，保证实时同步。
 */
object ConfigStore {

    private const val TAG = "[Config]"

    private const val ROOT_DIR = "Mayi_Tool"
    private const val CONFIG_DIR = "config"
    private const val CONFIG_FILE = "nekora.conf"

    /** 宿主进程和模块 App 都往这里找 */
    private val SEARCH_ROOTS = arrayOf(
        "/storage/emulated/0/Download",
        "/sdcard/Download",
        "/storage/emulated/0",
        "/sdcard",
    )

    private var file: File? = null
    private var props = Properties()
    private var lastMtime = 0L
    private var lastCheck = 0L

    // ==================== 初始化 ====================

    /** 模块 App 内调用：优先用 Context 拿外部目录，失败退回路径探测 */
    fun initInApp(ctx: Context) {
        if (file != null) return
        val base = runCatching { ctx.getExternalFilesDir(null) }.getOrNull()
        // 优先标准路径，写不进去再退回 app 私有外置目录
        val candidates = ArrayList<File>()
        candidates.add(File("/storage/emulated/0/Download/$ROOT_DIR/$CONFIG_DIR"))
        candidates.add(File("/sdcard/Download/$ROOT_DIR/$CONFIG_DIR"))
        base?.let {
            candidates.add(File(it.parentFile, "Download/$ROOT_DIR/$CONFIG_DIR"))
            candidates.add(File(it, "$ROOT_DIR/$CONFIG_DIR"))
        }
        candidates.addAll(SEARCH_ROOTS.map { File(it, "$ROOT_DIR/$CONFIG_DIR") })

        var chosen: File? = null
        for (dir in candidates) {
            val ok = runCatching {
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, CONFIG_FILE)
                if (!f.exists()) f.createNewFile()
                f.canRead() && f.canWrite()
            }.getOrDefault(false)
            if (ok) { chosen = dir; break }
        }

        if (chosen == null) {
            Log.w("$TAG no writable config dir, using app-private fallback")
            chosen = base ?: File("/data/local/tmp")
        }

        file = File(chosen, CONFIG_FILE)
        Log.i("$TAG config file = ${file!!.absolutePath}")
        reload(force = true)
    }

    /** 宿主进程内调用：只有路径探测（宿主没有模块的 Context） */
    fun initInHost() {
        if (file != null) return

        // 先找已存在的
        for (root in SEARCH_ROOTS) {
            val f = File(root, "$ROOT_DIR/$CONFIG_DIR/$CONFIG_FILE")
            if (f.exists() && f.canRead()) {
                file = f
                Log.i("$TAG host found config = ${f.absolutePath}")
                reload(force = true)
                return
            }
        }

        // 不存在就建到第一个可写位置
        for (root in SEARCH_ROOTS) {
            val dir = File(root, "$ROOT_DIR/$CONFIG_DIR")
            val ok = runCatching {
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, CONFIG_FILE)
                if (!f.exists()) f.createNewFile()
                f.canRead()
            }.getOrDefault(false)
            if (ok) {
                file = File(dir, CONFIG_FILE)
                Log.i("$TAG host created config = ${file!!.absolutePath}")
                reload(force = true)
                return
            }
        }
        Log.w("$TAG host cannot reach config file")
    }

    // ==================== 读写 ====================

    /** 每次访问前检查文件是否被另一端改过 */
    private fun sync() {
        val f = file ?: return
        val now = System.currentTimeMillis()
        if (now - lastCheck < 300L) return   // 300ms 内不重复 stat
        lastCheck = now
        val mt = runCatching { f.lastModified() }.getOrDefault(0L)
        if (mt != lastMtime) reload(force = false)
    }

    private fun reload(force: Boolean) {
        val f = file ?: return
        runCatching {
            val mt = f.lastModified()
            if (!force && mt == lastMtime) return
            val p = Properties()
            if (f.exists() && f.length() > 0) {
                FileInputStream(f).use { p.load(it) }
            }
            props = p
            lastMtime = mt
            if (force) Log.i("$TAG loaded ${p.size} key(s)")
        }.onFailure { Log.w("$TAG reload failed: ${it.message}") }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            FileOutputStream(f).use { props.store(it, "Nekora Lite config") }
            lastMtime = f.lastModified()
        }.onFailure { Log.w("$TAG save failed: ${it.message}") }
    }

    fun getString(key: String, def: String = ""): String {
        sync()
        return props.getProperty(key) ?: def
    }

    fun setString(key: String, v: String) {
        props.setProperty(key, v)
        save()
    }

    fun getBool(key: String, def: Boolean = false): Boolean {
        sync()
        return when (props.getProperty(key)?.lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> def
        }
    }

    fun setBool(key: String, v: Boolean) {
        props.setProperty(key, if (v) "true" else "false")
        save()
    }

    fun getInt(key: String, def: Int = 0): Int {
        sync()
        return props.getProperty(key)?.toIntOrNull() ?: def
    }

    fun setInt(key: String, v: Int) {
        props.setProperty(key, v.toString())
        save()
    }

    fun getLong(key: String, def: Long = 0L): Long {
        sync()
        return props.getProperty(key)?.toLongOrNull() ?: def
    }

    fun setLong(key: String, v: Long) {
        props.setProperty(key, v.toString())
        save()
    }

    /** 多行文本用 \n 分隔存成一行 */
    fun multiLine(key: String): List<String> =
        getString(key).split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun setMultiLine(key: String, list: List<String>) =
        setString(key, list.joinToString("\n"))

    /** 集合用 \u0001 分隔 */
    fun getSet(key: String): Set<String> {
        val raw = getString(key)
        if (raw.isEmpty()) return emptySet()
        return raw.split('\u0001').filter { it.isNotEmpty() }.toSet()
    }

    fun setSet(key: String, v: Set<String>) =
        setString(key, v.joinToString("\u0001"))

    fun path(): String = file?.absolutePath ?: "(unresolved)"
}
