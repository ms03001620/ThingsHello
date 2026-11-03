package org.mark.mobile.repository

import android.util.Log
import org.mark.mobile.utils.PreferUtils

/**
 * 手机端本地配置仓库接口（最近连接地址、端口、限速百分比）
 */
interface ICarPrefsRepository {
    fun getRecentAddresses(maxCount: Int = 5): List<String>
    fun getInitialHost(): String?
    fun getInitialPort(): String?
    fun saveRecentAddress(host: String, port: Int)
    fun getSavedSpeed(defaultSpeed: Int): Int
    fun saveSpeed(speed: Int)
}

/**
 * 默认实现，封装 [PreferUtils]
 */
class CarPrefsRepository(
    private val preferUtils: PreferUtils
) : ICarPrefsRepository {

    companion object {
        private const val TAG = "CarPrefsRepo"
    }

    override fun getRecentAddresses(maxCount: Int): List<String> {
        return preferUtils.getAddress().take(maxCount)
    }

    override fun getInitialHost(): String? {
        return preferUtils.getAddress().firstOrNull()
    }

    override fun getInitialPort(): String? {
        return if (preferUtils.hasCached() && preferUtils.portNumber > 0) {
            preferUtils.port
        } else {
            null
        }
    }

    override fun saveRecentAddress(host: String, port: Int) {
        preferUtils.add(host)
        preferUtils.save(host, port)
        Log.d(TAG, "recent saved $host:$port")
    }

    override fun getSavedSpeed(defaultSpeed: Int): Int = preferUtils.getSpeed(defaultSpeed)

    override fun saveSpeed(speed: Int) {
        preferUtils.saveSpeed(speed)
    }
}
