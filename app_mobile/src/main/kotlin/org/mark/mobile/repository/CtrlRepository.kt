package org.mark.mobile.repository

import android.util.Log
import org.mark.lib_unit_socket.ClientMessageCallback
import org.mark.lib_unit_socket.bean.BuzzerCmd
import org.mark.lib_unit_socket.bean.CameraServoCmd
import org.mark.lib_unit_socket.bean.CmdConstant
import org.mark.lib_unit_socket.bean.LightCmd
import org.mark.lib_unit_socket.bean.WheelCmd
import org.mark.lib_unit_socket.bean.WheelRotateCmd
import org.mark.mobile.connect.ConnectedManager
import org.mark.mobile.ctrl.Throttle
import org.mark.mobile.utils.PreferUtils

/**
 * 遥控页面数据仓库接口，隔离连接状态、控制命令发送、节流器与偏好配置，便于单元测试
 */
interface ICtrlRepository {
    fun isConnected(): Boolean
    fun getConnectedHost(): String?
    fun getConnectedPort(): Int

    fun addCallback(callback: ClientMessageCallback)
    fun removeCallback(callback: ClientMessageCallback)

    fun getSavedSpeed(defaultSpeed: Int): Int
    fun saveSpeed(speed: Int)
    fun saveRecentAddress(host: String, port: Int)

    fun submitServo(progress: Int)
    fun cancelServo()

    fun submitWheel(left: Int, right: Int)
    fun stopWheelNow()

    fun sendWheelRotate(rotate: WheelRotateCmd.Rotate, speed: Int)
    fun sendLight(enable: Boolean)
    fun sendBuzzer(enable: Boolean)
}

/**
 * 默认实现，封装 [PreferUtils]、[ConnectedManager] 与 [Throttle]
 */
class CtrlRepository(
    private val preferUtils: PreferUtils,
    private val connectedManager: ConnectedManager = ConnectedManager.getInstance()
) : ICtrlRepository {

    companion object {
        private const val TAG = "CtrlRepository"
        private const val SEND_INTERVAL_MS = 50L
    }

    private val servoThrottle = Throttle<Int>(SEND_INTERVAL_MS) { v ->
        Log.d(TAG, "servo $v")
        connectedManager.sendObject(CameraServoCmd(v), CmdConstant.CAMERA_SERVO)
    }

    private val wheelThrottle = Throttle<String>(SEND_INTERVAL_MS) { v ->
        val lr = v.split(",")
        val cmd = WheelCmd(lr[0].toInt(), lr[1].toInt())
        connectedManager.sendObject(cmd, CmdConstant.WHEEL)
    }

    override fun isConnected(): Boolean = connectedManager.isConnected

    override fun getConnectedHost(): String? = connectedManager.connectedHost

    override fun getConnectedPort(): Int = connectedManager.connectedPort

    override fun addCallback(callback: ClientMessageCallback) {
        connectedManager.addCallback(callback)
    }

    override fun removeCallback(callback: ClientMessageCallback) {
        connectedManager.removeCallback(callback)
    }

    override fun getSavedSpeed(defaultSpeed: Int): Int = preferUtils.getSpeed(defaultSpeed)

    override fun saveSpeed(speed: Int) {
        preferUtils.saveSpeed(speed)
    }

    override fun saveRecentAddress(host: String, port: Int) {
        preferUtils.add(host, port)
        preferUtils.save(host, port)
        Log.d(TAG, "recent saved $host:$port")
    }

    override fun submitServo(progress: Int) {
        servoThrottle.submit(progress)
    }

    override fun cancelServo() {
        servoThrottle.cancel()
    }

    override fun submitWheel(left: Int, right: Int) {
        wheelThrottle.submit("$left,$right")
    }

    override fun stopWheelNow() {
        wheelThrottle.sendNow("0,0")
    }

    override fun sendWheelRotate(rotate: WheelRotateCmd.Rotate, speed: Int) {
        connectedManager.sendObject(WheelRotateCmd(rotate, speed), CmdConstant.WHEEL_ROTATE)
    }

    override fun sendLight(enable: Boolean) {
        val cmd = LightCmd().apply { isEnable = enable }
        connectedManager.sendObject(cmd, CmdConstant.LIGHT)
    }

    override fun sendBuzzer(enable: Boolean) {
        connectedManager.sendObject(BuzzerCmd(enable), CmdConstant.BUZZER)
    }
}
