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
import org.mark.mobile.FakeCarPrefsRepository
import org.mark.mobile.FakeUiScheduler

class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeControlRepo: FakeCarControlRepository
    private lateinit var fakePrefsRepo: FakeCarPrefsRepository
    private lateinit var fakeScheduler: FakeUiScheduler
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        fakeControlRepo = FakeCarControlRepository().apply {
            connected = false
            host = null
            port = 0
        }
        fakePrefsRepo = FakeCarPrefsRepository()
        fakeScheduler = FakeUiScheduler()
        viewModel = MainViewModel(fakeControlRepo, fakePrefsRepo, scheduler = fakeScheduler)
    }

    @Test
    fun `initial state loads cached host, port and recent addresses`() {
        val state = viewModel.uiState.value!!
        assertEquals(ConnectStatus.IDLE, state.status)
        assertEquals(Pair("192.168.1.100", "8010"), viewModel.initialInput.value)
        assertEquals(listOf("192.168.1.100", "192.168.1.101"), viewModel.recentAddresses.value)
    }

    @Test
    fun `empty host or port triggers input error and host highlight`() {
        viewModel.attemptConnect("", "8010")
        val state = viewModel.uiState.value!!
        assertTrue(state.hasInputError)
        assertTrue(state.highlightHostError)
        assertEquals(ConnectStatus.IDLE, state.status)

        viewModel.onHostTextChanged()
        assertFalse(viewModel.uiState.value!!.hasInputError)
    }

    @Test
    fun `invalid port triggers input error without host highlight`() {
        viewModel.attemptConnect("192.168.1.100", "70000")
        val state = viewModel.uiState.value!!
        assertTrue(state.hasInputError)
        assertFalse(state.highlightHostError)
        assertEquals(ConnectStatus.IDLE, state.status)
    }

    @Test
    fun `connect success transitions to ONLINE, navigates after delay, and voluntary disconnect returns to IDLE`() {
        viewModel.attemptConnect("192.168.1.20", "8010")
        assertEquals(ConnectStatus.CONNECTING, viewModel.uiState.value!!.status)
        assertEquals("192.168.1.20", fakeControlRepo.host)
        assertEquals(8010, fakeControlRepo.port)

        fakeControlRepo.emitStatus(ClientMessageCallback.Status.CONNECTING)
        assertEquals(ConnectStatus.CONNECTING, viewModel.uiState.value!!.status)

        // Connected
        fakeControlRepo.connected = true
        fakeControlRepo.emitStatus(ClientMessageCallback.Status.CONNECTED)
        assertEquals(ConnectStatus.ONLINE, viewModel.uiState.value!!.status)
        assertNull(viewModel.navigateToCtrl.value)

        // Trigger delayed task (350ms)
        fakeScheduler.runPendingDelayed()
        val navEvent = viewModel.navigateToCtrl.value
        assertNotNull(navEvent)
        assertEquals(Pair("192.168.1.20", 8010), navEvent!!.getContentIfNotHandled())
        assertNull(navEvent.getContentIfNotHandled())

        // Voluntary disconnect when backing out of CtrlActivity returns to IDLE (not REFUSED)
        fakeControlRepo.connected = false
        fakeControlRepo.emitStatus(ClientMessageCallback.Status.NO_CONNECT)
        assertEquals(ConnectStatus.IDLE, viewModel.uiState.value!!.status)
    }

    @Test
    fun `cold-start connection timeout via ConnectedThread log+NO_CONNECT sets REFUSED with isTimeoutError true`() {
        viewModel.attemptConnect("192.168.1.20", "8010")
        fakeControlRepo.emitStatus(ClientMessageCallback.Status.CONNECTING)
        fakeControlRepo.emitLogMessage(
            "Thread-1开始连接Socket",
            Exception("failed to connect: ETIMEDOUT (Connection timed out)")
        )
        fakeControlRepo.emitStatus(ClientMessageCallback.Status.NO_CONNECT)

        val state = viewModel.uiState.value!!
        assertEquals(ConnectStatus.REFUSED, state.status)
        assertTrue(state.isTimeoutError)
    }

    @Test
    fun `cold-start connection refused via ConnectedThread log+NO_CONNECT sets REFUSED with isTimeoutError false`() {
        viewModel.attemptConnect("192.168.1.20", "8010")
        fakeControlRepo.emitStatus(ClientMessageCallback.Status.CONNECTING)
        fakeControlRepo.emitLogMessage(
            "Thread-1开始连接Socket",
            Exception("Connection refused")
        )
        fakeControlRepo.emitStatus(ClientMessageCallback.Status.NO_CONNECT)

        val state = viewModel.uiState.value!!
        assertEquals(ConnectStatus.REFUSED, state.status)
        assertFalse(state.isTimeoutError)
    }

    @Test
    fun `online state reconnects and validates when user changes host or port instead of navigating to stale car`() {
        viewModel.attemptConnect("192.168.1.20", "8010")
        fakeControlRepo.connected = true
        fakeControlRepo.emitStatus(ClientMessageCallback.Status.CONNECTED)
        assertEquals(ConnectStatus.ONLINE, viewModel.uiState.value!!.status)

        // Same host & port while connected -> navigates directly without reconnecting
        assertFalse(viewModel.onConnectClicked("192.168.1.20", "8010"))
        assertEquals(Pair("192.168.1.20", 8010), viewModel.navigateToCtrl.value!!.getContentIfNotHandled())

        // Invalid new host while ONLINE -> shows input error, does NOT navigate
        viewModel.onConnectClicked("", "8010")
        assertTrue(viewModel.uiState.value!!.hasInputError)
        assertNull(viewModel.navigateToCtrl.value!!.getContentIfNotHandled())

        // Different car IP while ONLINE -> initiates new connection to 192.168.1.99
        assertTrue(viewModel.onConnectClicked("192.168.1.99", "8010"))
        assertEquals(ConnectStatus.CONNECTING, viewModel.uiState.value!!.status)
        assertEquals("192.168.1.99", fakeControlRepo.host)
    }

    @Test
    fun `onConnectionRefusedExtra disconnects and shows REFUSED`() {
        viewModel.onConnectionRefusedExtra()
        assertTrue(fakeControlRepo.disconnectCalled)
        assertEquals(ConnectStatus.REFUSED, viewModel.uiState.value!!.status)
    }
}
