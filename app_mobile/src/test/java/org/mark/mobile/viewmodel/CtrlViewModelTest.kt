package org.mark.mobile.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mark.lib_unit_socket.ClientMessageCallback
import org.mark.lib_unit_socket.bean.WheelCmd
import org.mark.lib_unit_socket.bean.WheelRotateCmd
import org.mark.mobile.repository.ICtrlRepository

class CtrlViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeRepo: FakeCtrlRepository
    private lateinit var fakeScheduler: FakeUiScheduler

    @Before
    fun setUp() {
        fakeRepo = FakeCtrlRepository()
        fakeScheduler = FakeUiScheduler()
    }

    private fun createViewModel(isDemo: Boolean = false): CtrlViewModel {
        return CtrlViewModel(fakeRepo, isDemo = isDemo, scheduler = fakeScheduler)
    }

    @Test
    fun `initial state loads saved speed and connected subtitle`() {
        fakeRepo.savedSpeed = 65
        val vm = createViewModel()
        val state = vm.uiState.value!!

        assertEquals("192.168.8.100:8010 · 已连接", state.subtitleText)
        assertTrue(state.isConnected)
        assertEquals(65, state.speedLimit)
        assertEquals(50, state.servoPercent)
        assertFalse(state.isLightOn)
        assertTrue(state.showNoSignal)
        assertFalse(state.showLiveTag)
        assertNull(vm.returnToConnectRefused.value)
    }

    @Test
    fun `invalid saved speed falls back to DEFAULT_SPEED`() {
        fakeRepo.savedSpeed = 150
        val vm = createViewModel()
        assertEquals(WheelCmd.DEFAULT_SPEED, vm.uiState.value!!.speedLimit)
    }

    @Test
    fun `disconnected on init emits single-shot returnToConnectRefused`() {
        fakeRepo.connected = false
        val vm = createViewModel()

        val event = vm.returnToConnectRefused.value
        assertNotNull(event)
        assertNotNull(event!!.getContentIfNotHandled())
        // Idempotent across subsequent disconnect callbacks
        fakeRepo.emitStatus(ClientMessageCallback.Status.NO_CONNECT)
        fakeRepo.emitException(Exception("Broken pipe"))
        assertNull(vm.returnToConnectRefused.value!!.getContentIfNotHandled())
    }

    @Test
    fun `demo mode initializes preset values and ignores disconnect`() {
        fakeRepo.connected = false
        val vm = createViewModel(isDemo = true)
        val state = vm.uiState.value!!

        assertEquals("192.168.8.190:8010 · 已连接", state.subtitleText)
        assertTrue(state.isConnected)
        assertEquals(65, state.servoPercent)
        assertEquals(72, state.speedLimit)
        assertEquals("300", state.bufferMsText)
        assertEquals("30", state.fpsText)
        assertEquals("4.2", state.bitrateText)
        assertEquals(-52, state.wifiRssi)
        assertTrue(state.isLightOn)
        assertTrue(state.showLiveTag)
        assertNull(vm.returnToConnectRefused.value)
    }

    @Test
    fun `onResume saves recent address after delay and onPause cancels pending save`() {
        val vm = createViewModel()

        // Cancelled before delay fires
        vm.onResume("192.168.8.100", 8010)
        vm.onPause()
        fakeScheduler.runPendingDelayed()
        assertNull(fakeRepo.savedRecent)

        // Survives full delay -> saved once
        vm.onResume("192.168.8.100", 8010)
        fakeScheduler.runPendingDelayed()
        assertEquals(Pair("192.168.8.100", 8010), fakeRepo.savedRecent)
        assertEquals(1, fakeRepo.saveRecentCount)

        // Subsequent onResume does not save again
        vm.onResume("192.168.8.100", 8010)
        fakeScheduler.runPendingDelayed()
        assertEquals(1, fakeRepo.saveRecentCount)
    }

    @Test
    fun `joystick forward and release send mixed wheel speed and stopNow`() {
        fakeRepo.savedSpeed = 60
        val vm = createViewModel()

        // Forward (angle = 90) at 50% stick -> maxSpeed = 30 -> (30, 30)
        vm.onJoystickMove(90, 0.5f)
        assertEquals(Pair(30, 30), fakeRepo.lastWheelSubmitted)

        // Deadzone / release -> stopWheelNow
        vm.onJoystickMove(-1, 0f)
        assertEquals(1, fakeRepo.stopWheelNowCount)
    }

    @Test
    fun `rotate buttons send left and right rotate commands with speed limit and zero on release`() {
        fakeRepo.savedSpeed = 55
        val vm = createViewModel()

        vm.onRotatePressed(isLeft = true)
        assertEquals(Pair(WheelRotateCmd.Rotate.LEFT, 55), fakeRepo.lastRotateSent)

        vm.onRotateReleased(isLeft = true)
        assertEquals(Pair(WheelRotateCmd.Rotate.LEFT, 0), fakeRepo.lastRotateSent)

        vm.onRotatePressed(isLeft = false)
        assertEquals(Pair(WheelRotateCmd.Rotate.RIGHT, 55), fakeRepo.lastRotateSent)
    }

    @Test
    fun `servo, recenter, speed and light actions update state and repository`() {
        val vm = createViewModel()

        vm.onServoChanged(80)
        assertEquals(80, vm.uiState.value!!.servoPercent)
        assertEquals(80, fakeRepo.lastServoSubmitted)

        vm.onRecenterServo()
        assertEquals(50, vm.uiState.value!!.servoPercent)
        assertEquals(50, fakeRepo.lastServoSubmitted)

        vm.onSpeedChanged(75)
        assertEquals(75, vm.uiState.value!!.speedLimit)
        assertEquals(75, fakeRepo.savedSpeed)

        vm.onToggleLight()
        assertTrue(vm.uiState.value!!.isLightOn)
        assertEquals(listOf(true), fakeRepo.lightSentHistory)

        // Sync from H264Activity updates state without resending LightCmd
        vm.syncLightFromPreview(false)
        assertFalse(vm.uiState.value!!.isLightOn)
        assertEquals(listOf(true), fakeRepo.lightSentHistory)
    }

    @Test
    fun `horn repeats while pressed and stops on release or onStop`() {
        val vm = createViewModel()

        vm.onHornDown()
        assertTrue(vm.uiState.value!!.isHornPressed)
        assertEquals(1, fakeRepo.buzzerCount)

        // 400ms tick #1
        fakeScheduler.runPendingDelayed()
        assertEquals(2, fakeRepo.buzzerCount)

        // Release stops future ticks
        vm.onHornUp()
        assertFalse(vm.uiState.value!!.isHornPressed)
        fakeScheduler.runPendingDelayed()
        assertEquals(2, fakeRepo.buzzerCount)

        // onStop cancels horn, cancels servo, and stops wheels
        vm.onHornDown()
        assertEquals(3, fakeRepo.buzzerCount)
        vm.onStop()
        assertFalse(vm.uiState.value!!.isHornPressed)
        assertEquals(1, fakeRepo.cancelServoCount)
        assertEquals(1, fakeRepo.stopWheelNowCount)
        fakeScheduler.runPendingDelayed()
        assertEquals(3, fakeRepo.buzzerCount)
    }

    @Test
    fun `wifi rssi and video telemetry update uiState`() {
        val vm = createViewModel()

        vm.onWifiRssiUpdated(-60)
        assertEquals(-60, vm.uiState.value!!.wifiRssi)
        assertFalse(vm.uiState.value!!.isWifiWeak)

        vm.onWifiRssiUpdated(-80)
        assertEquals(-80, vm.uiState.value!!.wifiRssi)
        assertTrue(vm.uiState.value!!.isWifiWeak)

        vm.onVideoLiveChanged(true)
        assertFalse(vm.uiState.value!!.showNoSignal)
        assertTrue(vm.uiState.value!!.showLiveTag)

        vm.onVideoStatsUpdated("185", "29", "3.8")
        assertEquals("185", vm.uiState.value!!.bufferMsText)
        assertEquals("29", vm.uiState.value!!.fpsText)
        assertEquals("3.8", vm.uiState.value!!.bitrateText)

        vm.onVideoLiveChanged(false)
        assertNull(vm.uiState.value!!.bufferMsText)
        assertNull(vm.uiState.value!!.fpsText)
        assertNull(vm.uiState.value!!.bitrateText)
    }

    private class FakeUiScheduler : UiScheduler {
        private val delayedTasks = mutableListOf<() -> Unit>()

        override fun runOnUi(block: () -> Unit) {
            block()
        }

        override fun postDelayed(delayMillis: Long, block: () -> Unit) {
            delayedTasks.add(block)
        }

        override fun cancelAll() {
            delayedTasks.clear()
        }

        fun runPendingDelayed() {
            val copy = delayedTasks.toList()
            delayedTasks.clear()
            copy.forEach { it.invoke() }
        }
    }

    private class FakeCtrlRepository : ICtrlRepository {
        var connected: Boolean = true
        var host: String? = "192.168.8.100"
        var port: Int = 8010
        var savedSpeed: Int = WheelCmd.DEFAULT_SPEED
        var savedRecent: Pair<String, Int>? = null
        var saveRecentCount: Int = 0

        var callback: ClientMessageCallback? = null
        var lastServoSubmitted: Int? = null
        var cancelServoCount: Int = 0
        var lastWheelSubmitted: Pair<Int, Int>? = null
        var stopWheelNowCount: Int = 0
        var lastRotateSent: Pair<WheelRotateCmd.Rotate, Int>? = null
        val lightSentHistory = mutableListOf<Boolean>()
        var buzzerCount: Int = 0

        override fun isConnected(): Boolean = connected
        override fun getConnectedHost(): String? = host
        override fun getConnectedPort(): Int = port

        override fun addCallback(callback: ClientMessageCallback) {
            this.callback = callback
        }

        override fun removeCallback(callback: ClientMessageCallback) {
            if (this.callback === callback) {
                this.callback = null
            }
        }

        override fun getSavedSpeed(defaultSpeed: Int): Int = savedSpeed

        override fun saveSpeed(speed: Int) {
            savedSpeed = speed
        }

        override fun saveRecentAddress(host: String, port: Int) {
            savedRecent = Pair(host, port)
            saveRecentCount++
        }

        override fun submitServo(progress: Int) {
            lastServoSubmitted = progress
        }

        override fun cancelServo() {
            cancelServoCount++
        }

        override fun submitWheel(left: Int, right: Int) {
            lastWheelSubmitted = Pair(left, right)
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

        fun emitException(e: Exception) {
            callback?.onExceptionToReOpen(e)
        }
    }
}
