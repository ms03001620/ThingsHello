package org.mark.mobile.repository

import org.mark.lib_unit_socket.ClientMessageCallback
import org.mark.mobile.connect.ConnectedManager
import org.mark.mobile.utils.PreferUtils

/**
 * 主页面数据仓库接口，隔离连接管理与本地偏好设置，便于单元测试
 */
interface IMainRepository {
    fun getRecentAddresses(maxCount: Int = 5): List<String>
    fun getInitialHost(): String?
    fun getInitialPort(): String?
    fun connect(host: String, port: Int)
    fun disconnect()
    fun isConnected(): Boolean
    fun addCallback(callback: ClientMessageCallback)
    fun removeCallback(callback: ClientMessageCallback)
}

/**
 * 默认实现，封装 [PreferUtils] 与 [ConnectedManager]
 */
class MainRepository(
    private val preferUtils: PreferUtils,
    private val connectedManager: ConnectedManager = ConnectedManager.getInstance()
) : IMainRepository {

    override fun getRecentAddresses(maxCount: Int): List<String> {
        val raw = preferUtils.address ?: return emptyList()
        return raw.filter { it.isNotEmpty() }.take(maxCount)
    }

    override fun getInitialHost(): String? {
        val raw = preferUtils.address ?: return null
        return raw.firstOrNull { it.isNotEmpty() }
    }

    override fun getInitialPort(): String? {
        return if (preferUtils.hasCached() && preferUtils.portNumber > 0) {
            preferUtils.port
        } else {
            null
        }
    }

    override fun connect(host: String, port: Int) {
        connectedManager.stop()
        connectedManager.init(host, port)
    }

    override fun disconnect() {
        connectedManager.stop()
    }

    override fun isConnected(): Boolean {
        return connectedManager.isConnected
    }

    override fun addCallback(callback: ClientMessageCallback) {
        connectedManager.addCallback(callback)
    }

    override fun removeCallback(callback: ClientMessageCallback) {
        connectedManager.removeCallback(callback)
    }
}
