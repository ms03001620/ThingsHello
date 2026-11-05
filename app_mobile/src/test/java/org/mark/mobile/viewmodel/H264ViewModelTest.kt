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
import org.mark.mobile.FakeCarControlRepository
import org.mark.mobile.FakeUiScheduler

class H264ViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeControlRepo: FakeCarControlRepository
    private lateinit var fakeScheduler: FakeUiScheduler

    @Before
    fun setUp() {
        fakeControlRepo = FakeCarControlRepository()
        fakeScheduler = FakeUiScheduler()
    }

    private fun createViewModel(
        isDemo: Boolean = false,
        debugState: String? = null,
        initialLightOn: Boolean = false,
        servoPercent: Int = 50,
        speedLimit: Int = -1
    ): H264ViewModel {
        return H264ViewModel(
            controlRepository = fakeControlRepo,
            isDemo = isDemo,
            debugState = debugState,
            initialLightOn = initialLightOn,
            servoPercent = servoPercent,
            speedLimit = speedLimit,
            scheduler = fakeScheduler
        )
    }

    @Test
    fun `initial state formats servo, speed limit and schedules hint and hud auto-hide`() {
        val vm = createViewModel(initialLightOn = true, servoPercent = 65, speedLimit = 80)
        val state = vm.uiState.value!!

        assertFalse(state.isLive)
        assertEquals("NO SIGNAL", state.noSignalText)
        assertTrue(state.isHudVisible)
        assertTrue(state.isHintVisible)
        assertTrue(state.isLightOn)
        assertEquals("云台 65%", state.servoPillText)
        assertEquals("限速 80%", state.speedPillText)
        assertNull(vm.finishPreview.value)

        fakeScheduler.advanceBy(H264ViewModel.HINT_HIDE_DELAY_MS)
        assertFalse(vm.uiState.value!!.isHintVisible)
        assertTrue(vm.uiState.value!!.isHudVisible)
        assertFalse(state.autoHideEnabled)

        // 默认关闭自动隐藏：HUD 常驻
        fakeScheduler.advanceBy(10_000L)
        assertTrue(vm.uiState.value!!.isHudVisible)
    }

    @Test
    fun `auto-hide toggle enables timer and disabling cancels and shows hud`() {
        val vm = createViewModel()
        vm.onToggleAutoHide()
        assertTrue(vm.uiState.value!!.autoHideEnabled)
        fakeScheduler.advanceBy(H264ViewModel.HUD_AUTO_HIDE_MS)
        assertFalse(vm.uiState.value!!.isHudVisible)

        vm.onToggleAutoHide()
        assertFalse(vm.uiState.value!!.autoHideEnabled)
        assertTrue(vm.uiState.value!!.isHudVisible)
        fakeScheduler.advanceBy(10_000L)
        assertTrue(vm.uiState.value!!.isHudVisible)

        // 双击仍可切换
        vm.onScreenDoubleTap(hasPlayerError = false)
        assertFalse(vm.uiState.value!!.isHudVisible)
        fakeScheduler.advanceBy(10_000L)
        assertFalse(vm.uiState.value!!.isHudVisible)
        vm.onScreenDoubleTap(hasPlayerError = false)
        assertTrue(vm.uiState.value!!.isHudVisible)
    }

    @Test
    fun `negative speed limit renders placeholder`() {
        val vm = createViewModel(speedLimit = -1)
        assertEquals("限速 --", vm.uiState.value!!.speedPillText)
    }

    @Test
    fun `demo and demo_hidden modes initialize preset telemetry and disable auto-hide`() {
        fakeControlRepo.connected = false
        val demoVm = createViewModel(isDemo = true, debugState = "demo")
        val demoState = demoVm.uiState.value!!

        assertTrue(demoState.isLive)
        assertTrue(demoState.isHudVisible)
        assertEquals("CSI · 1280×720 · 30FPS · RTSP", demoState.streamPillText)
        assertEquals("BUF 300ms", demoState.bufPillText)
        assertEquals("手机 WiFi -52dBm", demoState.wifiPillText)
        assertEquals("4.2Mbps", demoState.ratePillText)
        assertEquals("云台 65%", demoState.servoPillText)
        assertEquals("限速 72%", demoState.speedPillText)

        demoVm.onStart()
        fakeScheduler.advanceBy(10_000L)
        assertTrue(demoVm.uiState.value!!.isHudVisible)
        assertNull(demoVm.finishPreview.value)

        val hiddenVm = createViewModel(isDemo = true, debugState = "demo_hidden")
        assertFalse(hiddenVm.uiState.value!!.isHudVisible)
    }

    @Test
    fun `toggle light sends command, updates state and bumps hud`() {
        val vm = createViewModel(initialLightOn = false)
        vm.onToggleAutoHide()
        fakeScheduler.advanceBy(H264ViewModel.HUD_AUTO_HIDE_MS)
        assertFalse(vm.uiState.value!!.isHudVisible)

        vm.onToggleLight()
        assertTrue(vm.uiState.value!!.isLightOn)
        assertTrue(vm.uiState.value!!.isHudVisible)
        assertEquals(listOf(true), fakeControlRepo.lightSentHistory)

        vm.onToggleLight()
        assertFalse(vm.uiState.value!!.isLightOn)
        assertEquals(listOf(true, false), fakeControlRepo.lightSentHistory)
    }

    @Test
    fun `player error and double tap retry toggle hud and reset noSignalText`() {
        val vm = createViewModel()

        vm.onPlayerError()
        assertFalse(vm.uiState.value!!.isLive)
        assertEquals("NO SIGNAL · 双击重试", vm.uiState.value!!.noSignalText)

        val retry = vm.onScreenDoubleTap(hasPlayerError = true)
        assertTrue(retry)
        assertEquals("NO SIGNAL", vm.uiState.value!!.noSignalText)
        assertFalse(vm.uiState.value!!.isHudVisible)

        val retry2 = vm.onScreenDoubleTap(hasPlayerError = false)
        assertFalse(retry2)
        assertTrue(vm.uiState.value!!.isHudVisible)
    }

    @Test
    fun `hud stats update pills accurately`() {
        val vm = createViewModel()

        vm.onVideoLiveChanged(true)
        assertTrue(vm.uiState.value!!.isLive)

        vm.onHudStatsUpdated(
            rssi = -58,
            hasPlayer = true,
            isReady = true,
            bufferedMs = 92L,
            videoWidth = 1280,
            videoHeight = 720,
            fps = "30",
            rateMbps = "3.6"
        )
        val readyState = vm.uiState.value!!
        assertEquals("手机 WiFi -58dBm", readyState.wifiPillText)
        assertEquals("BUF 92ms", readyState.bufPillText)
        assertEquals("CSI · 1280×720 · 30FPS · RTSP", readyState.streamPillText)
        assertEquals("3.6Mbps", readyState.ratePillText)

        vm.onHudStatsUpdated(
            rssi = null,
            hasPlayer = false,
            isReady = false,
            bufferedMs = 0L,
            videoWidth = 0,
            videoHeight = 0,
            fps = null,
            rateMbps = null
        )
        val idleState = vm.uiState.value!!
        assertEquals("手机 WiFi --", idleState.wifiPillText)
        assertEquals("BUF --", idleState.bufPillText)
        assertEquals("CSI · -- · --FPS · RTSP", idleState.streamPillText)
        assertEquals("--Mbps", idleState.ratePillText)
    }

    @Test
    fun `disconnect or back request emits single-shot finishPreview with latest light state`() {
        val vm = createViewModel(initialLightOn = false)
        vm.onToggleLight()
        vm.onStart()

        fakeControlRepo.connected = false
        fakeScheduler.advanceBy(H264ViewModel.LINK_WATCH_POLL_MS)

        val event = vm.finishPreview.value
        assertNotNull(event)
        assertEquals(true, event!!.getContentIfNotHandled())

        fakeControlRepo.emitStatus(ClientMessageCallback.Status.NO_CONNECT)
        vm.onBackRequested()
        assertNull(vm.finishPreview.value!!.getContentIfNotHandled())
    }

    @Test
    fun `catch-up playback speed follows stepped thresholds and hysteresis`() {
        val vm = createViewModel()
        assertEquals(1.0f, vm.computeCatchUpSpeed(false, 500L, 1.25f), 0.001f)
        assertEquals(1.25f, vm.computeCatchUpSpeed(true, 351L, 1.0f), 0.001f)
        assertEquals(1.15f, vm.computeCatchUpSpeed(true, 200L, 1.0f), 0.001f)
        assertEquals(1.05f, vm.computeCatchUpSpeed(true, 100L, 1.0f), 0.001f)
        assertEquals(1.05f, vm.computeCatchUpSpeed(true, 60L, 1.15f), 0.001f)
        assertEquals(1.0f, vm.computeCatchUpSpeed(true, 60L, 1.0f), 0.001f)
        assertEquals(1.0f, vm.computeCatchUpSpeed(true, 45L, 1.05f), 0.001f)
    }
}
