package top.nekora.clone.hook.entry

import android.app.Activity
import android.view.View
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect
import top.nekora.clone.util.ViewFinder
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections

/**
 * 快手设置页入口（完整还原原版 vv.java [KSS-SettingsEntry]）。
 *
 * **关键修正：必须在数据层插入，不能在视图层 addView。**
 *
 * 原版做法：
 *   1. hook SettingsActivity 的 onCreate / onResume 触发
 *   2. 45 秒追踪窗口内反复尝试（设置页数据是异步加载的）
 *   3. 找到「自定义 action getter」——零参、非 static、
 *      返回类型名含 setting/action 且不是 java./android./androidx.
 *   4. hook 这些 getter，在返回值里塞我们的条目
 *   5. 宿主自己渲染 → 视图回收时重建的仍是宿主渲染的 → 永久存在
 *
 * 视图层 addView 会被宿主重新布局丢弃，这是此前「日志说 injected 但看不到」的根本原因。
 */
object SettingsEntryHook {

    private const val TAG = "[SettingsEntry]"

    // 与原版一致的标识
    private const val ITEM_KEY = "nekora_custom_settings"
    private const val ITEM_TITLE = "Xposed 自定义设置"
    private const val ITEM_URI = "kwai://setting/nekora_custom_settings"

    /** 原版 f10719a */
    private val SETTINGS_ACTIVITIES = listOf(
        "com.yxcorp.plugin.setting.activity.SettingsActivity",
    )

    /** 原版 f10721a：设置列表 Fragment */
    private val SETTINGS_FRAGMENTS = listOf(
        "com.yxcorp.gifshow.settings.page.SettingItemListFragment",
        "com.yxcorp.plugin.setting.stencil.SettingItemListStencilFragment",
    )

    /** 原版 f10742a：KEY 字段名 */
    private val KEY_FIELDS = arrayOf("itemkey", "settingkey", "key", "id")

    /** 原版 f10743a：TITLE 字段名 */
    private val TITLE_FIELDS = arrayOf("title", "name", "text", "display")

    /** 原版 f10736a：跳过字段（图标、描述、链接等不改） */
    private val SKIP_FIELDS = arrayOf(
        "icon", "image", "img", "url", "uri", "desc", "description",
        "subtitle", "sub_title", "summary", "tips", "hint", "right", "extra", "tag",
    )

    /** 原版 f10740a：列表字段名 */
    private val LIST_FIELDS = arrayOf(
        "items", "itemlist", "settingitems", "settingitemlist", "children", "list", "data",
    )

    /** 原版 f10741a：分组列表字段名 */
    private val GROUP_FIELDS = arrayOf(
        "groups", "grouplist", "settinggroups", "settinggrouplist", "pages", "pagelist",
    )

    private val hookedClasses = Collections.synchronizedSet(HashSet<String>())
    private val hookedAdapters = Collections.synchronizedSet(HashSet<Any>())
    private var currentActivity: Activity? = null
    private var injected = false

    fun install(module: XposedModule, loader: ClassLoader, pkg: String) {
        var count = 0

        // ① Activity 生命周期
        for (name in SETTINGS_ACTIVITIES) {
            val cls = Reflect.findClass(loader, name) ?: continue
            if (hookLifecycle(module, cls, pkg)) count++
        }

        // ② Fragment 生命周期
        for (name in SETTINGS_FRAGMENTS) {
            val cls = Reflect.findClass(loader, name) ?: continue
            if (hookLifecycle(module, cls, pkg)) count++
        }

        if (count > 0) Log.hooked("$TAG hooked $count class(es) pkg=$pkg")
        else Log.w("$TAG no settings class found pkg=$pkg")
    }

    // ==================== 生命周期 hook ====================

    private fun hookLifecycle(module: XposedModule, cls: Class<*>, pkg: String): Boolean {
        if (!hookedClasses.add(cls.name)) return false
        var count = 0

        for (name in arrayOf("onCreate", "onResume")) {
            val m = Reflect.findMethodInHierarchy(cls, name) ?: continue
            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    val r = chain.proceed()
                    val self = chain.thisObject
                    if (self is Activity) currentActivity = self
                    runCatching { onPageShown(module, self, pkg) }
                        .onFailure { Log.w("$TAG page handling failed: ${it.message}") }
                    r
                }
                count++
                Log.i("$TAG hooked ${cls.simpleName}.$name")
            }.onFailure { Log.w("$TAG hook $name failed: ${it.message}") }
        }
        return count > 0
    }

    // ==================== 数据层注入 ====================

    /**
     * 与原版 m5241a 一致：45 秒内反复尝试。
     * 设置页数据是异步加载的，一次不够。
     */
    private fun onPageShown(module: XposedModule, self: Any?, pkg: String) {
        if (!Prefs.getBool("settings_entry_enabled", true)) return
        if (injected) return

        val deadline = System.currentTimeMillis() + 45_000L

        Thread {
            while (System.currentTimeMillis() < deadline && !injected) {
                runCatching { tryInject(module, self, pkg) }
                    .onFailure { Log.w("$TAG inject attempt: ${it.message}") }
                if (!injected) Thread.sleep(500L)
            }
            if (!injected) Log.w("$TAG 45s timeout, entry not injected")
        }.apply { isDaemon = true }.start()
    }

    /** 一次注入尝试：直接找 RecyclerView 的 adapter，hook 它的数据方法 */
    private fun tryInject(module: XposedModule, self: Any?, pkg: String) {
        val root = viewOf(self) ?: return

        // 找 View 树里所有的 RecyclerView / ListView
        val recyclers = ArrayList<View>()
        ViewFinder.walk(root, maxDepth = 14) { v, _ ->
            val n = v.javaClass.name
            if (n.contains("RecyclerView") || n.contains("ListView")) {
                recyclers.add(v)
            }
        }

        if (recyclers.isEmpty()) {
            Log.i("$TAG 等待 RecyclerView…")
            return
        }

        var hookedAny = false
        for (rv in recyclers) {
            val adapter = getAdapter(rv) ?: continue
            if (hookAdapter(module, adapter, pkg)) hookedAny = true
        }

        if (hookedAny) {
            injected = true
            Log.i("$TAG ★ adapter hook 安装完成（${recyclers.size} 个列表）")
        }
    }

    /** 拿 RecyclerView 的 adapter */
    private fun getAdapter(v: View): Any? {
        for (name in arrayOf("getAdapter")) {
            val m = Reflect.findMethodInHierarchy(v.javaClass, name)
            if (m != null && m.parameterTypes.isEmpty()) {
                runCatching { return m.invoke(v) }
            }
        }
        for (name in arrayOf("mAdapter", "adapter")) {
            val f = Reflect.findField(v.javaClass, name)
            if (f != null) {
                runCatching { return f.get(v) }
            }
        }
        return null
    }

    /**
     * hook adapter 的数据方法。
     * 关键：找返回 List 或 Collection 的无参方法，插入我们的条目。
     */
    private fun hookAdapter(module: XposedModule, adapter: Any, pkg: String): Boolean {
        if (!hookedAdapters.add(adapter)) return false

        var count = 0
        for (m in adapter.javaClass.methods) {
            if (m.parameterTypes.isNotEmpty()) continue
            if (Modifier.isStatic(m.modifiers)) continue

            val rt = m.returnType
            val isListLike = List::class.java.isAssignableFrom(rt) ||
                Collection::class.java.isAssignableFrom(rt)
            if (!isListLike) continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    val result = chain.proceed()
                    runCatching { injectIntoList(result, pkg) }
                        .getOrElse { result }
                }
                count++
                Log.i("$TAG hooked ${adapter.javaClass.simpleName}.${m.name} -> ${rt.simpleName}")
            }
        }
        return count > 0
    }

    /** 往列表里插入条目 */
    @Suppress("UNCHECKED_CAST")
    private fun injectIntoList(result: Any?, pkg: String): Any? {
        if (result == null) return null

        val list: MutableList<Any?> = when (result) {
            is MutableList<*> -> result as MutableList<Any?>
            is Collection<*> -> return run {
                val copy = ArrayList<Any?>(result)
                if (copy.none { isOurs(it) }) {
                    buildEntry(copy.firstOrNull())?.let { copy.add(0, it) }
                }
                copy
            }
            else -> return result
        }

        if (list.any { isOurs(it) }) return result

        // 找模板：优先用已有的设置项
        val template = list.firstOrNull { it != null }
        val entry = buildEntry(template) ?: return result

        runCatching {
            list.add(0, entry)
            Log.i("$TAG ★ 已插入设置项，列表 size=${list.size}")
        }
        return result
    }

    /** 从 Fragment/Activity 拿根 View */
    private fun viewOf(self: Any?): View? {
        if (self == null) return null
        if (self is Activity) return self.window?.decorView
        for (name in arrayOf("getView", "mView", "view")) {
            Reflect.findMethodInHierarchy(self.javaClass, name)?.let { m ->
                runCatching { m.invoke(self) as? View }.getOrNull()?.let { return it }
            }
            Reflect.findField(self.javaClass, name)?.let { f ->
                runCatching { f.get(self) as? View }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    /**
     * 遍历 View 树，收集持有设置数据的对象：
     *   - RecyclerView 的 adapter
     *   - adapter 里的 item 对象
     *   - 各种 Presenter / Model
     */
    private fun collectDataHolders(root: View, out: MutableList<Any>) {
        walk(root, 0) { v, depth ->
            if (depth > 12) return@walk

            // RecyclerView / ListView 的 adapter
            for (fname in arrayOf("mAdapter", "adapter", "getAdapter")) {
                val f = Reflect.findField(v.javaClass, fname)
                if (f != null) {
                    runCatching { f.get(v)?.let { if (it !in out) out.add(it) } }
                }
                val m = Reflect.findMethodInHierarchy(v.javaClass, fname)
                if (m != null && m.parameterTypes.isEmpty()) {
                    runCatching { m.invoke(v)?.let { if (it !in out) out.add(it) } }
                }
            }

            // 类名含 setting 的任意对象
            val cn = v.javaClass.name.lowercase()
            if (cn.contains("setting") && v !in out) out.add(v)
        }
    }

    /**
     * 原版 m5256a 的完整还原：
     * 找「自定义 action getter」并 hook，在返回值里插入条目。
     */
    private fun hookActionGetters(module: XposedModule, obj: Any, pkg: String): Boolean {
        val methods = runCatching { obj.javaClass.methods }.getOrNull() ?: return false

        var count = 0
        for (m in methods) {
            if (!isCandidateGetter(m)) continue

            runCatching {
                m.isAccessible = true
                module.hook(m).intercept { chain ->
                    val result = chain.proceed()
                    runCatching { injectInto(result, pkg) }
                        .getOrElse { result }
                }
                count++
                Log.i("$TAG hooked getter ${obj.javaClass.simpleName}.${m.name} -> ${m.returnType.simpleName}")
            }.onFailure { Log.w("$TAG hook ${m.name} failed: ${it.message}") }
        }
        return count > 0
    }

    /** 原版 m5256a 的判定逻辑 */
    private fun isCandidateGetter(m: Method): Boolean {
        if (m.parameterTypes.isNotEmpty()) return false
        if (Modifier.isStatic(m.modifiers)) return false

        val rt = m.returnType.name.lowercase()

        // 排除基础类型和系统库
        if (rt.startsWith("java.") || rt.startsWith("android.") || rt.startsWith("androidx.")) {
            // 但 List/Collection 要放行（那是容器）
            val isContainer = List::class.java.isAssignableFrom(m.returnType) ||
                Collection::class.java.isAssignableFrom(m.returnType)
            if (!isContainer) return false
        }

        val name = m.name
        // 方法名：含 action，或以 get 开头且长度 > 3
        val nameOk = name.contains("action", true) ||
            (name.startsWith("get") && name.length > 3)

        // 返回类型：含 setting 或 action
        val typeOk = rt.contains("setting") || rt.contains("action")

        return nameOk && typeOk
    }

    // ==================== 往返回值里插入 ====================

    /** 按返回类型分派插入方式 */
    @Suppress("UNCHECKED_CAST")
    private fun injectInto(result: Any?, pkg: String): Any? {
        if (result == null) return null

        when (result) {
            is MutableList<*> -> {
                val list = result as MutableList<Any?>
                if (list.any { isOurs(it) }) return result
                val entry = buildEntry(list.firstOrNull()) ?: return result
                runCatching {
                    list.add(0, entry)
                    Log.i("$TAG ★ inserted into List, size=${list.size}")
                }
                return result
            }
            is Collection<*> -> {
                // 不可变集合：包一层新的
                val copy = ArrayList<Any?>(result)
                if (copy.none { isOurs(it) }) {
                    val entry = buildEntry(copy.firstOrNull()) ?: return result
                    copy.add(0, entry)
                    Log.i("$TAG ★ inserted into Collection (copied)")
                    return copy
                }
                return result
            }
            else -> {
                // 单对象：可能是 SettingPageStencil，里面含 list 字段
                injectIntoFields(result, pkg)
                return result
            }
        }
    }

    /** 单对象里找列表字段插入 */
    @Suppress("UNCHECKED_CAST")
    private fun injectIntoFields(obj: Any, pkg: String) {
        var c: Class<*>? = obj.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (!List::class.java.isAssignableFrom(f.type)) continue
                runCatching {
                    f.isAccessible = true
                    val v = f.get(obj) as? MutableList<Any?> ?: return@runCatching
                    if (v.any { isOurs(it) }) return@runCatching
                    val entry = buildEntry(v.firstOrNull()) ?: return@runCatching
                    v.add(0, entry)
                    Log.i("$TAG ★ inserted into field '${f.name}', size=${v.size}")
                }
            }
            c = c.superclass
        }
    }

    private fun isOurs(item: Any?): Boolean {
        if (item == null) return false
        for (name in KEY_FIELDS) {
            val v = Reflect.getFieldValue(item, name)
            if (v is String && v == ITEM_KEY) return true
        }
        return false
    }

    /**
     * 造条目对象。
     * 以列表里第一个元素为模板，反射 newInstance 后改字段。
     * 这样能保证类型与被 hook 的列表一致。
     */
    private fun buildEntry(template: Any?): Any? {
        val cls = template?.javaClass ?: return null
        val obj = runCatching {
            val ctor = cls.declaredConstructors.minByOrNull { it.parameterTypes.size }
                ?: return@runCatching null
            ctor.isAccessible = true
            ctor.newInstance(*Array(ctor.parameterTypes.size) { defaultOf(ctor.parameterTypes[it]) })
        }.getOrNull() ?: return null

        // 按原版字段名清单填值
        fillByNames(obj, TITLE_FIELDS, ITEM_TITLE, skip = SKIP_FIELDS)
        fillByNames(obj, KEY_FIELDS, ITEM_KEY, skip = SKIP_FIELDS)
        fillByNames(obj, arrayOf("type", "actiontype", "itemactiontype"), "PAGE", skip = emptyArray())
        fillByNames(obj, arrayOf("url", "uri", "schema", "scheme", "pageurl", "targeturl", "actionurl", "link"), ITEM_URI, skip = emptyArray())

        Log.i("$TAG built entry as ${cls.simpleName}")
        return obj
    }

    private fun defaultOf(t: Class<*>): Any? = when (t) {
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Boolean::class.javaPrimitiveType -> false
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }

    /** 按字段名清单填字符串，跳过 SKIP_FIELDS */
    private fun fillByNames(obj: Any, names: Array<String>, value: String, skip: Array<String>) {
        var c: Class<*>? = obj.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (f.type != String::class.java) continue
                val fn = f.name.lowercase()
                if (skip.any { fn == it }) continue
                if (names.any { fn == it }) {
                    runCatching {
                        f.isAccessible = true
                        f.set(obj, value)
                    }
                }
            }
            c = c.superclass
        }
    }

    private fun walk(root: View, depth: Int, block: (View, Int) -> Unit) {
        block(root, depth)
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) walk(root.getChildAt(i), depth + 1, block)
        }
    }
}
