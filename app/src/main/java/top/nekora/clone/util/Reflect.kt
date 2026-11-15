package top.nekora.clone.util

import java.lang.reflect.Method

/**
 * 反射辅助。
 *
 * 宿主混淆后同一类里同名方法极多，必须按参数类型过滤，
 * 否则 findMethod 会拿到错误的重载。这里带二级缓存。
 */
object Reflect {

    private val methodCache = HashMap<String, Method?>()
    private val fieldCache = HashMap<String, java.lang.reflect.Field?>()

    fun findClass(loader: ClassLoader, name: String): Class<*>? =
        runCatching { Class.forName(name, false, loader) }.getOrNull()

    /**
     * 按方法名查找，params 为 null 表示不限制参数（取第一个匹配）。
     */
    fun findMethod(
        cls: Class<*>,
        name: String,
        vararg params: Class<*>?,
    ): Method? {
        val key = cls.name + "#" + name + "#" + params.joinToString(",") { it?.name ?: "*" }
        methodCache[key]?.let { return it }

        val found = runCatching {
            if (params.isEmpty()) {
                cls.declaredMethods.firstOrNull { it.name == name }
            } else {
                cls.declaredMethods.firstOrNull { m ->
                    m.name == name &&
                        m.parameterTypes.size == params.size &&
                        m.parameterTypes.indices.all { i ->
                            val want = params[i]
                            want == null || want.isAssignableFrom(m.parameterTypes[i]) ||
                                m.parameterTypes[i].isAssignableFrom(want)
                        }
                }
            }?.apply { isAccessible = true }
        }.getOrNull()

        methodCache[key] = found
        return found
    }

    /** 递归向上找，父类里的方法也能命中 */
    fun findMethodInHierarchy(
        cls: Class<*>,
        name: String,
        vararg params: Class<*>?,
    ): Method? {
        var c: Class<*>? = cls
        while (c != null) {
            findMethod(c, name, *params)?.let { return it }
            c = c.superclass
        }
        return null
    }

    fun findField(cls: Class<*>, name: String): java.lang.reflect.Field? {
        val key = cls.name + "#" + name
        fieldCache[key]?.let { return it }

        var c: Class<*>? = cls
        var found: java.lang.reflect.Field? = null
        while (c != null) {
            found = runCatching { c.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()
            if (found != null) break
            c = c.superclass
        }
        fieldCache[key] = found
        return found
    }

    /** 按类型在所有字段里找（混淆改了名时用） */
    fun findFieldByType(cls: Class<*>, type: Class<*>): java.lang.reflect.Field? {
        var c: Class<*>? = cls
        while (c != null) {
            c.declaredFields.firstOrNull { it.type == type }?.let {
                it.isAccessible = true
                return it
            }
            c = c.superclass
        }
        return null
    }

    fun getFieldValue(any: Any, name: String): Any? =
        runCatching { findField(any.javaClass, name)?.get(any) }.getOrNull()

    fun setFieldValue(any: Any, name: String, value: Any?) {
        runCatching { findField(any.javaClass, name)?.set(any, value) }
    }
}
