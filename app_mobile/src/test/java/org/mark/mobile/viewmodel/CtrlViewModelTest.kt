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
import org.mark.mobile.FakeCarControlRepository
import org.mark.mobile.FakeCarPrefsRepository
import org.mark.mobile.FakeUiScheduler
import org.mark.mobile.ctrl.WheelSpeed
import org.mark.mobile.usecase.HoldHornUseCase
import org.mark.mobile.usecase.SaveRecentCarUseCase

class CtrlViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeControlRepo: FakeCarControlRepository
    private lateinit var fakePrefsRepo: FakeCarPrefsRepository
    private lateinit var fakeScheduler: FakeUiScheduler

    @Before
    fun setUp() {
        fakeControlRepo = FakeCarControlRepository()
        fakePrefsRepo = FakeCarPrefsRepository()
        fakeScheduler = FakeUiScheduler()
    }

    private fun createViewModel(isDemo: Boolean = false): CtrlViewModel {
        return CtrlViewModel(
            controlRepository = fakeControlRepo,
            prefsRepository = fakePrefsRepo,
            isDemo = isDemo,
            scheduler = fakeScheduler
        )
    }

    @Test
    fun `initial state loads saved speed and connected subtitle`() {
        fakePrefsRepo.savedSpeed = 65
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
        fakePrefsRepo.savedSpeed = 150
        val vm = createViewModel()
        assertEquals(WheelCmd.DEFAULT_SPEED, vm.uiState.value!!.speedLimit)
    }

    @Test
    fun `disconnected on init emits single-shot returnToConnectRefused`() {
        fakeControlRepo.connected = false
        val vm = createViewModel()

        val event = vm.returnToConnectRefused.value
        assertNotNull(event)
        assertNotNull(event!!.getContentIfNotHandled())
        fakeControlRepo.emitStatus(ClientMessageCallback.Status.NO_CONNECT)
        fakeControlRepo.emitException(Exception("Broken pipe"))
        assertNull(vm.returnToConnectRefused.value!!.getContentIfNotHandled())
    }

    @Test
    fun `demo mode initializes preset values and ignores disconnect`() {
        fakeControlRepo.connected = false
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

        vm.onResume("192.168.8.100", 8010)
        vm.onPause()
        fakeScheduler.advanceBy(SaveRecentCarUseCase.SAVE_DELAY_MS)
        assertNull(fakePrefsRepo.savedRecent)

        vm.onResume("192.168.8.100", 8010)
        fakeScheduler.advanceBy(SaveRecentCarUseCase.SAVE_DELAY_MS)
        assertEquals(Pair("192.168.8.100", 8010), fakePrefsRepo.savedRecent)
        assertEquals(1, fakePrefsRepo.saveRecentCount)

        vm.onResume("192.168.8.100", 8010)
        fakeScheduler.advanceBy(SaveRecentCarUseCase.SAVE_DELAY_MS)
        assertEquals(1, fakePrefsRepo.saveRecentCount)
    }

    @Test
    fun `onResume saves actual connected car address rather than mismatched intent extra`() {
        fakeControlRepo.host = "192.168.8.100"
        fakeControlRepo.port = 8010
        val vm = createViewModel()

        vm.onResume("invalid-stale-extra", 9999)
        fakeScheduler.advanceBy(SaveRecentCarUseCase.SAVE_DELAY_MS)
        assertEquals(Pair("192.168.8.100", 8010), fakePrefsRepo.savedRecent)
    }

    @Test
    fun `onActivityFinishing stops motion, disconnects TCP and suppresses returnToConnectRefused`() {
        val vm = createViewModel()
        vm.onHornDown()

        vm.onActivityFinishing()
        fakeControlRepo.emitStatus(ClientMessageCallback.Status.NO_CONNECT)

        assertFalse(vm.uiState.value!!.isHornPressed)
        assertEquals(1, fakeControlRepo.cancelServoCount)
        assertEquals(1, fakeControlRepo.stopWheelNowCount)
        assertTrue(fakeControlRepo.disconnectCalled)
        assertNull(vm.returnToConnectRefused.value)
    }

    @Test
    fun `joystick forward and release send mixed wheel speed and stopNow`() {
        fakePrefsRepo.savedSpeed = 60
        val vm = createViewModel()

        vm.onJoystickMove(90, 0.5f)
        assertEquals(WheelSpeed(30, 30), fakeControlRepo.lastWheelSubmitted)

        vm.onJoystickMove(-1, 0f)
        assertEquals(1, fakeControlRepo.stopWheelNowCount)
    }

    @Test
    fun `rotate buttons send left and right rotate commands with speed limit and zero on release`() {
        fakePrefsRepo.savedSpeed = 55
        val vm = createViewModel()

        vm.onRotatePressed(isLeft = true)
        assertEquals(Pair(WheelRotateCmd.Rotate.LEFT, 55), fakeControlRepo.lastRotateSent)

        vm.onRotateReleased(isLeft = true)
        assertEquals(Pair(WheelRotateCmd.Rotate.LEFT, 0), fakeControlRepo.lastRotateSent)

        vm.onRotatePressed(isLeft = false)
        assertEquals(Pair(WheelRotateCmd.Rotate.RIGHT, 55), fakeControlRepo.lastRotateSent)
    }

    @Test
    fun `servo, recenter, speed and light actions update state and repository`() {
        val vm = createViewModel()

        vm.onServoChanged(80)
        assertEquals(80, vm.uiState.value!!.servoPercent)
        assertEquals(80, fakeControlRepo.lastServoSubmitted)

        vm.onRecenterServo()
        assertEquals(50, vm.uiState.value!!.servoPercent)
        assertEquals(50, fakeControlRepo.lastServoSubmitted)

        vm.onSpeedChanged(75)
        assertEquals(75, vm.uiState.value!!.speedLimit)
        assertEquals(75, fakePrefsRepo.savedSpeed)

        vm.onToggleLight()
        assertTrue(vm.uiState.value!!.isLightOn)
        assertEquals(listOf(true), fakeControlRepo.lightSentHistory)

        vm.syncLightFromPreview(false)
        assertFalse(vm.uiState.value!!.isLightOn)
        assertEquals(listOf(true), fakeControlRepo.lightSentHistory)
    }

    @Test
    fun `horn repeats while pressed and stops on release or onStop`() {
        val vm = createViewModel()

        vm.onHornDown()
        assertTrue(vm.uiState.value!!.isHornPressed)
        assertEquals(1, fakeControlRepo.buzzerCount)

        fakeScheduler.advanceBy(HoldHornUseCase.REPEAT_MS)
        assertEquals(2, fakeControlRepo.buzzerCount)

        vm.onHornUp()
        assertFalse(vm.uiState.value!!.isHornPressed)
        fakeScheduler.advanceBy(HoldHornUseCase.REPEAT_MS)
        assertEquals(2, fakeControlRepo.buzzerCount)

        vm.onHornDown()
        assertEquals(3, fakeControlRepo.buzzerCount)
        vm.onStop()
        assertFalse(vm.uiState.value!!.isHornPressed)
        assertEquals(1, fakeControlRepo.cancelServoCount)
        assertEquals(1, fakeControlRepo.stopWheelNowCount)
        fakeScheduler.advanceBy(HoldHornUseCase.REPEAT_MS)
        assertEquals(3, fakeControlRepo.buzzerCount)
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
}
