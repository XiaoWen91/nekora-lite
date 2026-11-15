package top.nekora.clone.dexkit

import android.content.Context
import android.content.SharedPreferences
import top.nekora.clone.util.Log

/**
 * DexKit 查找结果缓存。
 *
 * 与原版一致的设计：
 *   - 缓存键 = 宿主版本 + apk 路径 + apk 修改时间
 *   - 缓存内容 = 每条查找规则 -> 解析出的类名
 *   - 命中后直接用 Class.forName，不再扫 dex
 *   - 宿主升级（版本号/路径/mtime 任一变化）缓存自动失效
 *
 * 原版还额外用 nativeOpenDexKitCache 加密本地文件、并用 /api/dexkit/cache
 * 做跨用户共享；本实现只用明文 SharedPreferences，足够自用。
 */
object DexCache {

    private const val TAG = "[DexCache]"
    private const val PREF = "nekora_dexkit_cache"

    private const val K_HOST_VERSION = "cache_host_version"
    private const val K_APK_PATH = "cache_apk_path"
    private const val K_APK_MTIME = "cache_apk_mtime"
    private const val K_SCHEMA = "cache_schema"
    private const val PREFIX = "sym_"

    /** 每次改动查找规则就 +1，旧缓存自动作废 */
    private const val SCHEMA = 1

    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    fun initWith(module: io.github.libxposed.api.XposedModule) {
        runCatching { sp = module.getRemotePreferences(PREF) }
    }

    /**
     * 判断缓存是否仍然有效。
     * 宿主版本、apk 路径、apk 修改时间任一变化，都视为失效。
     */
    fun isValid(hostVersion: String, apkPath: String, apkMtime: Long): Boolean {
        val p = sp ?: return false
        if (p.getInt(K_SCHEMA, 0) != SCHEMA) {
            Log.i("$TAG invalid: schema changed")
            return false
        }
        if (p.getString(K_HOST_VERSION, null) != hostVersion) {
            Log.i("$TAG invalid: host version ${p.getString(K_HOST_VERSION, null)} -> $hostVersion")
            return false
        }
        if (p.getString(K_APK_PATH, null) != apkPath) {
            Log.i("$TAG invalid: apk path changed")
            return false
        }
        if (p.getLong(K_APK_MTIME, -1L) != apkMtime) {
            Log.i("$TAG invalid: apk updated")
            return false
        }
        Log.i("$TAG valid, reusing cached symbols")
        return true
    }

    fun beginWrite(hostVersion: String, apkPath: String, apkMtime: Long) {
        val p = sp ?: return
        val e = p.edit().clear()
        e.putInt(K_SCHEMA, SCHEMA)
        e.putString(K_HOST_VERSION, hostVersion)
        e.putString(K_APK_PATH, apkPath)
        e.putLong(K_APK_MTIME, apkMtime)
        e.apply()
    }

    fun put(key: String, className: String) {
        sp?.edit()?.putString(PREFIX + key, className)?.apply()
        Log.i("$TAG saved $key -> $className")
    }

    fun get(key: String): String? = sp?.getString(PREFIX + key, null)

    fun clear() {
        sp?.edit()?.clear()?.apply()
        Log.i("$TAG cleared")
    }

    /** 缓存里已存了多少条符号 */
    fun count(): Int = sp?.all?.keys?.count { it.startsWith(PREFIX) } ?: 0
}
