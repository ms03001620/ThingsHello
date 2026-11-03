package org.mark.mobile

import org.mark.lib_unit_socket.ClientMessageCallback
import org.mark.lib_unit_socket.bean.WheelCmd
import org.mark.lib_unit_socket.bean.WheelRotateCmd
import org.mark.mobile.ctrl.WheelSpeed
import org.mark.mobile.repository.ICarControlRepository
import org.mark.mobile.repository.ICarPrefsRepository
import org.mark.mobile.utils.UiScheduler

class FakeUiScheduler : UiScheduler {
    private data class ScheduledTask(val runAtMs: Long, val block: () -> Unit)

    var nowMs: Long = 0L
        private set

    private val tasks = mutableListOf<ScheduledTask>()

    override fun runOnUi(block: () -> Unit) {
        block()
    }

    override fun postDelayed(delayMillis: Long, block: () -> Unit) {
        tasks.add(ScheduledTask(nowMs + delayMillis, block))
    }

    override fun cancelAll() {
        tasks.clear()
    }

    fun advanceBy(deltaMs: Long) {
        val target = nowMs + deltaMs
        while (true) {
            val next = tasks.filter { it.runAtMs <= target }.minByOrNull { it.runAtMs } ?: break
            tasks.remove(next)
            nowMs = next.runAtMs
            next.block.invoke()
        }
        nowMs = target
    }

    fun runPendingDelayed() {
        val copy = tasks.sortedBy { it.runAtMs }
        tasks.clear()
        copy.forEach {
            nowMs = it.runAtMs
            it.block.invoke()
        }
    }
}

class FakeCarControlRepository : ICarControlRepository {
    var connected: Boolean = true
    var host: String? = "192.168.8.100"
    var port: Int = 8010
    var disconnectCalled: Boolean = false

    var callback: ClientMessageCallback? = null
    var lastServoSubmitted: Int? = null
    var cancelServoCount: Int = 0
    var lastWheelSubmitted: WheelSpeed? = null
    var stopWheelNowCount: Int = 0
    var lastRotateSent: Pair<WheelRotateCmd.Rotate, Int>? = null
    val lightSentHistory = mutableListOf<Boolean>()
    var buzzerCount: Int = 0

    override fun isConnected(): Boolean = connected
    override fun getConnectedHost(): String? = host
    override fun getConnectedPort(): Int = port

    override fun connect(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    override fun disconnect() {
        disconnectCalled = true
        connected = false
    }

    override fun addCallback(callback: ClientMessageCallback) {
        this.callback = callback
    }

    override fun removeCallback(callback: ClientMessageCallback) {
        if (this.callback === callback) {
            this.callback = null
        }
    }

    override fun submitServo(progress: Int) {
        lastServoSubmitted = progress
    }

    override fun cancelServo() {
        cancelServoCount++
    }

    override fun submitWheel(speed: WheelSpeed) {
        lastWheelSubmitted = speed
    }

    override fun stopWheelNow() {
        stopWheelNowCount++
    }

    override fun sendWheelRotate(rotate: WheelRotateCmd.Rotate, speed: Int) {
        lastRotateSent = Pair(rotate, speed)
    }

    override fun sendLight(enable: Boolean) {
        lightSentHistory.add(enable)
    }

    override fun sendBuzzer(enable: Boolean) {
        if (enable) buzzerCount++
    }

    fun emitStatus(status: ClientMessageCallback.Status) {
        callback?.onStatusChange(status)
    }

    fun emitLogMessage(message: String?, e: Exception? = null) {
        callback?.onLogMessage(message, e)
    }

    fun emitException(e: Exception) {
        callback?.onExceptionToReOpen(e)
    }
}

class FakeCarPrefsRepository : ICarPrefsRepository {
    var recent: List<String> = listOf("192.168.1.100", "192.168.1.101")
    var cachedHost: String? = "192.168.1.100"
    var cachedPort: String? = "8010"
    var savedSpeed: Int = WheelCmd.DEFAULT_SPEED
    var savedRecent: Pair<String, Int>? = null
    var saveRecentCount: Int = 0

    override fun getRecentAddresses(maxCount: Int): List<String> = recent.take(maxCount)
    override fun getInitialHost(): String? = cachedHost
    override fun getInitialPort(): String? = cachedPort

    override fun saveRecentAddress(host: String, port: Int) {
        savedRecent = Pair(host, port)
        saveRecentCount++
    }

    override fun getSavedSpeed(defaultSpeed: Int): Int = savedSpeed

    override fun saveSpeed(speed: Int) {
        savedSpeed = speed
    }
}
