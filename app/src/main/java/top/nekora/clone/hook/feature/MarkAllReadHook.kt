package top.nekora.clone.hook.feature

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.libxposed.api.XposedModule
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.SettingsItem
import top.nekora.clone.util.Log
import top.nekora.clone.util.Reflect
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier

/**
 * 一键已读（完整还原原版 lo.java [KwaiMarkAllRead]）。
 *
 * 位置：私信页左上角「+」弹出的菜单。
 *
 * 原版 m3708a 的完整算法：
 *   1. 去重：遍历列里每个对象的 String 字段，
 *      有任何一个 == "一键已读" 就返回（避免重复插入）
 *   2. 找构造器：严格匹配 (int, String, View.OnClickListener, int)
 *   3. 构造并加入：constructor.newInstance(0, "一键已读", listener, 1)
 *
 * 执行已读（m3710a）：
 *   1. 找 IMConversationManager 里 static + WeakReference 字段
 *   2. ((WeakReference) f.get(null)).get() 拿实例
 *   3. 沿类层级找零参方法
 *   4. method.invoke(manager)
 */
object MarkAllReadHook {

    private const val TAG = "[MarkAllRead]"

    private const val ITEM_TEXT = "一键已读"
    private const val CLS_CONV = "com.kwai.kmp.imsdk.internal.logic.conversation.IMConversationManager"

    private val READ_METHOD_NAMES = arrayOf(
        "markAllRead", "markAllReadInternal", "setAllRead", "clearAllUnread", "readAll",
    )

    /** 已处理过的列表，避免重复 hooK */
    private val handledLists = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<Any, Boolean>()
    )

    fun install(module: XposedModule, pkg: String) {
        hookViewAttach(module, pkg)
        startRequestWatcher()
        Log.hooked("$TAG install pkg=$pkg")
    }

    // ==================== 菜单注入 ====================

    private fun hookViewAttach(module: XposedModule, pkg: String) {
        val m = runCatching {
            View::class.java.getDeclaredMethod("onAttachedToWindow").apply { isAccessible = true }
        }.getOrNull() ?: return

        module.hook(m).intercept { chain ->
            val r = chain.proceed()
            val v = chain.thisObject as? ViewGroup
            if (v != null && Prefs.getBool(Prefs.K_NOTICE_MARK_ALL_READ)) {
                runCatching { tryInject(v, pkg) }
            }
            r
        }
        Log.i("$TAG hooked View.onAttachedToWindow")
    }

    /**
     * 在某个 ViewGroup 里找菜单列表。
     * 原版是从 WindowManagerGlobal.mViews 拿顶层窗口再找 List，
     * 这里改成从 View 树找 RecyclerView / LinearLayout 的 adapter 数据。
     */
    private fun tryInject(root: ViewGroup, pkg: String) {
        // 找含「发私信」或「添加朋友」的容器 = 菜单
        val text = collectText(root, 200)
        if (!text.contains("发私信") && !text.contains("添加朋友")) return
        if (text.contains(ITEM_TEXT)) return   // 已存在

        // 找菜单的列表容器
        val listHolder = findListHolder(root) ?: return
        injectInto(listHolder, pkg)
    }

    /** 找可能持有菜单数据的对象（adapter / 直接是 List） */
    private fun findListHolder(root: ViewGroup): Any? {
        var holder: Any? = null
        walk(root) { v ->
            if (holder != null) return@walk
            // RecyclerView 的 adapter
            for (name in arrayOf("mAdapter", "adapter")) {
                val f = Reflect.findField(v.javaClass, name)
                if (f != null) {
                    runCatching { f.get(v)?.let { holder = it } }
                }
            }
        }
        return holder
    }

    /**
     * 原版 m3708a 的完整还原：
     * 找菜单数据列表，按构造器签名 (int, String, OnClickListener, int) 造条目。
     */
    @Suppress("UNCHECKED_CAST")
    private fun injectInto(holder: Any?, pkg: String) {
        if (holder == null) return
        if (!handledLists.add(holder)) return

        // 找 holder 里的 List 字段
        var list: MutableList<Any?>? = null
        var c: Class<*>? = holder.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (!List::class.java.isAssignableFrom(f.type)) continue
                runCatching {
                    f.isAccessible = true
                    val v = f.get(holder) as? MutableList<Any?> ?: return@runCatching
                    if (v.isNotEmpty()) { list = v; return@runCatching }
                }
            }
            c = c.superclass
        }

        val target = list ?: run {
            Log.i("$TAG 未找到菜单列表")
            return
        }

        // 1. 去重：看有没有 String 字段 == "一键已读"
        for (item in target) {
            if (item == null) continue
            for (f in item.javaClass.declaredFields) {
                if (f.type != String::class.java) continue
                runCatching {
                    f.isAccessible = true
                    if (f.get(item) == ITEM_TEXT) {
                        Log.i("$TAG 已存在，跳过")
                        return
                    }
                }
            }
        }

        // 2. 找构造器签名 (int, String, View.OnClickListener, int)
        val sample = target.firstOrNull { it != null } ?: return
        var ctor: Constructor<*>? = null
        for (ct in sample.javaClass.declaredConstructors) {
            val pt = ct.parameterTypes
            if (pt.size == 4 &&
                pt[0] == Int::class.javaPrimitiveType &&
                pt[1] == String::class.java &&
                pt[2] == View.OnClickListener::class.java &&
                pt[3] == Int::class.javaPrimitiveType
            ) {
                ctor = ct
                break
            }
        }

        if (ctor == null) {
            Log.w("$TAG 未找到 (int,String,OnClickListener,int) 构造器，候选签名：")
            sample.javaClass.declaredConstructors.forEach {
                Log.w("$TAG   ${it.parameterTypes.joinToString { p -> p.simpleName }}")
            }
            return
        }

        // 3. 构造并加入
        runCatching {
            ctor.isAccessible = true
            val listener = View.OnClickListener {
                Log.i("$TAG 菜单项被点击")
                Prefs.setBool(Prefs.K_MARK_ALL_READ_REQUEST, true)
            }
            val item = ctor.newInstance(0, ITEM_TEXT, listener, 1)
            target.add(item)
            Log.i("$TAG ★ 菜单项已插入，size=${target.size}")
        }.onFailure { Log.w("$TAG 构造失败: ${it.message}") }
    }

    // ==================== 执行已读 ====================

    private fun startRequestWatcher() {
        Thread {
            while (true) {
                runCatching {
                    if (Prefs.getBool(Prefs.K_MARK_ALL_READ_REQUEST)) {
                        Prefs.setBool(Prefs.K_MARK_ALL_READ_REQUEST, false)
                        Log.i("$TAG 收到执行请求")
                        doMarkAllRead()
                    }
                }
                Thread.sleep(800L)
            }
        }.apply { isDaemon = true }.start()
        Log.i("$TAG request watcher started")
    }

    /** 原版 m3710a 的完整还原 */
    private fun doMarkAllRead() {
        val cls = runCatching { Class.forName(CLS_CONV) }.getOrNull() ?: run {
            Log.w("$TAG $CLS_CONV 不存在")
            return
        }

        // 1. 找 static + WeakReference 字段
        var instance: Any? = null
        for (f in cls.declaredFields) {
            if (!Modifier.isStatic(f.modifiers)) continue
            if (f.type != java.lang.ref.WeakReference::class.java) continue
            runCatching {
                f.isAccessible = true
                instance = (f.get(null) as? java.lang.ref.WeakReference<*>)?.get()
            }
        }

        if (instance == null) {
            Log.w("$TAG 会话管理器实例未找到")
            return
        }

        // 2. 沿类层级找零参方法
        var method: java.lang.reflect.Method? = null
        var c: Class<*>? = instance!!.javaClass
        while (c != null && c != Any::class.java && method == null) {
            for (m in c.declaredMethods) {
                if (m.parameterTypes.isNotEmpty()) continue
                if (READ_METHOD_NAMES.any { m.name.equals(it, true) }) {
                    method = m
                    break
                }
            }
            c = c.superclass
        }

        if (method == null) {
            Log.w("$TAG 未找到零参已读方法，类=${instance!!.javaClass.simpleName}")
            return
        }

        runCatching {
            method.isAccessible = true
            method.invoke(instance)
            Log.i("$TAG ★ 一键已读执行成功 via ${method.name}")
        }.onFailure { Log.w("$TAG 执行失败: ${it.message}") }
    }

    // ==================== 工具 ====================

    private fun collectText(root: View, max: Int): String {
        val sb = StringBuilder()
        walk(root) { v ->
            if (sb.length < max) {
                (v as? TextView)?.text?.let { sb.append(it) }
            }
        }
        return sb.toString()
    }

    private fun walk(root: View, block: (View) -> Unit) {
        fun rec(v: View, d: Int) {
            if (d > 10) return
            block(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) rec(v.getChildAt(i), d + 1)
        }
        runCatching { rec(root, 0) }
    }
}
