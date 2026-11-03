package org.mark.mobile.repository

import android.os.SystemClock
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
import org.mark.mobile.ctrl.WheelSpeed
import org.mark.mobile.utils.AndroidUiScheduler
import org.mark.mobile.utils.UiScheduler

/**
 * 小车控制与 TCP 连接领域仓库接口，统一服务连接页、遥控页与全屏预览页
 */
interface ICarControlRepository {
    fun isConnected(): Boolean
    fun getConnectedHost(): String?
    fun getConnectedPort(): Int

    fun connect(host: String, port: Int)
    fun disconnect()

    fun addCallback(callback: ClientMessageCallback)
    fun removeCallback(callback: ClientMessageCallback)

    fun submitServo(progress: Int)
    fun cancelServo()

    fun submitWheel(speed: WheelSpeed)
    fun stopWheelNow()

    fun sendWheelRotate(rotate: WheelRotateCmd.Rotate, speed: Int)
    fun sendLight(enable: Boolean)
    fun sendBuzzer(enable: Boolean)
}

/**
 * 默认实现，封装 [ConnectedManager] 与指令 [Throttle]
 */
class CarControlRepository(
    private val connectedManager: ConnectedManager = ConnectedManager,
    scheduler: UiScheduler = AndroidUiScheduler(),
    clock: () -> Long = SystemClock::uptimeMillis
) : ICarControlRepository {

    companion object {
        private const val TAG = "CarControlRepo"
        private const val SEND_INTERVAL_MS = 50L
    }

    private val servoThrottle = Throttle<Int>(SEND_INTERVAL_MS, scheduler, clock) { progress ->
        Log.d(TAG, "servo $progress")
        connectedManager.sendObject(CameraServoCmd(progress), CmdConstant.CAMERA_SERVO)
    }

    private val wheelThrottle = Throttle<WheelSpeed>(SEND_INTERVAL_MS, scheduler, clock) { speed ->
        connectedManager.sendObject(WheelCmd(speed.left, speed.right), CmdConstant.WHEEL)
    }

    override fun isConnected(): Boolean = connectedManager.isConnected

    override fun getConnectedHost(): String? = connectedManager.connectedHost

    override fun getConnectedPort(): Int = connectedManager.connectedPort

    override fun connect(host: String, port: Int) {
        connectedManager.stop()
        connectedManager.init(host, port)
    }

    override fun disconnect() {
        connectedManager.stop()
    }

    override fun addCallback(callback: ClientMessageCallback) {
        connectedManager.addCallback(callback)
    }

    override fun removeCallback(callback: ClientMessageCallback) {
        connectedManager.removeCallback(callback)
    }

    override fun submitServo(progress: Int) {
        servoThrottle.submit(progress)
    }

    override fun cancelServo() {
        servoThrottle.cancel()
    }

    override fun submitWheel(speed: WheelSpeed) {
        wheelThrottle.submit(speed)
    }

    override fun stopWheelNow() {
        wheelThrottle.sendNow(WheelSpeed(0, 0))
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
