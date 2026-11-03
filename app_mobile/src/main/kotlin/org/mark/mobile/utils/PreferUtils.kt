package org.mark.mobile.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * app_mobile 专用的 SharedPreferences 统一管理类（单例）。
 */
class PreferUtils private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun hasCached(): Boolean = prefs.contains(KEY_HOST) && prefs.contains(KEY_PORT)

    val portNumber: Int
        get() = prefs.getInt(KEY_PORT, 0)

    val port: String
        get() = portNumber.toString()

    fun save(host: String, port: Int) {
        prefs.edit()
            .putString(KEY_HOST, host)
            .putInt(KEY_PORT, port)
            .apply()
    }

    /**
     * 增加新地址，如果已存在则置顶作为最近使用
     */
    fun add(host: String) {
        val existing = getAddress()
        val updated = listOf(host) + existing.filter { it.isNotEmpty() && it != host }
        prefs.edit().putString(KEY_ADDRESS, updated.joinToString("#")).apply()
    }

    fun getAddress(): List<String> {
        val raw = prefs.getString(KEY_ADDRESS, null) ?: return emptyList()
        return raw.split("#").filter { it.isNotEmpty() }
    }

    fun getSpeed(defaultSpeed: Int): Int = prefs.getInt(KEY_SPEED, defaultSpeed)

    fun saveSpeed(speed: Int) {
        prefs.edit().putInt(KEY_SPEED, speed).apply()
    }

    companion object {
        private const val PREF_NAME = "client"
        private const val KEY_HOST = "key-host"
        private const val KEY_PORT = "key-port"
        private const val KEY_ADDRESS = "address"
        private const val KEY_SPEED = "config_speed"

        @Volatile
        private var instance: PreferUtils? = null

        fun getInstance(context: Context): PreferUtils {
            return instance ?: synchronized(this) {
                instance ?: PreferUtils(context).also { instance = it }
            }
        }
    }
}
