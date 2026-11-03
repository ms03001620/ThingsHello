package org.mark.mobile.repository

import org.mark.lib_unit_socket.ClientMessageCallback
import org.mark.lib_unit_socket.bean.CmdConstant
import org.mark.lib_unit_socket.bean.LightCmd
import org.mark.mobile.connect.ConnectedManager

/**
 * 全屏预览页（H264Activity）数据仓库接口，隔离 TCP 连接监控与车灯控制命令，便于单元测试
 */
interface IH264Repository {
    fun isConnected(): Boolean
    fun addCallback(callback: ClientMessageCallback)
    fun removeCallback(callback: ClientMessageCallback)
    fun sendLight(enable: Boolean)
}

/**
 * 默认实现，封装 [ConnectedManager]
 */
class H264Repository(
    private val connectedManager: ConnectedManager = ConnectedManager.getInstance()
) : IH264Repository {

    override fun isConnected(): Boolean = connectedManager.isConnected

    override fun addCallback(callback: ClientMessageCallback) {
        connectedManager.addCallback(callback)
    }

    override fun removeCallback(callback: ClientMessageCallback) {
        connectedManager.removeCallback(callback)
    }

    override fun sendLight(enable: Boolean) {
        val cmd = LightCmd().apply { isEnable = enable }
        connectedManager.sendObject(cmd, CmdConstant.LIGHT)
    }
}
