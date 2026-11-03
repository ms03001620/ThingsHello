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
import org.mark.mobile.repository.IMainRepository

class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeRepo: FakeMainRepository
    private lateinit var fakeScheduler: FakeUiScheduler
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        fakeRepo = FakeMainRepository()
        fakeScheduler = FakeUiScheduler()
        viewModel = MainViewModel(fakeRepo, fakeScheduler)
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

        // Typing clears error
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
    fun `connect success transitions to ONLINE and navigates after delay`() {
        viewModel.attemptConnect("192.168.1.20", "8010")
        assertEquals(ConnectStatus.CONNECTING, viewModel.uiState.value!!.status)
        assertEquals("192.168.1.20", fakeRepo.lastConnectedHost)
        assertEquals(8010, fakeRepo.lastConnectedPort)

        // First NO_CONNECT from previous stop() is ignored while CONNECTING
        fakeRepo.emitStatus(ClientMessageCallback.Status.NO_CONNECT)
        assertEquals(ConnectStatus.CONNECTING, viewModel.uiState.value!!.status)

        // Connected
        fakeRepo.connected = true
        fakeRepo.emitStatus(ClientMessageCallback.Status.CONNECTED)
        assertEquals(ConnectStatus.ONLINE, viewModel.uiState.value!!.status)
        assertNull(viewModel.navigateToCtrl.value)

        // Trigger delayed task (350ms)
        fakeScheduler.runPendingDelayed()
        val navEvent = viewModel.navigateToCtrl.value
        assertNotNull(navEvent)
        assertEquals(Pair("192.168.1.20", 8010), navEvent!!.getContentIfNotHandled())
        // Event is single-shot
        assertNull(navEvent.getContentIfNotHandled())
    }

    @Test
    fun `connection timeout failure sets REFUSED with isTimeoutError true`() {
        viewModel.attemptConnect("192.168.1.20", "8010")
        // Initial stop() NO_CONNECT
        fakeRepo.emitStatus(ClientMessageCallback.Status.NO_CONNECT)

        // Socket exception with timeout message
        fakeRepo.emitException(Exception("failed to connect: ETIMEDOUT (Connection timed out)"))
        val state = viewModel.uiState.value!!
        assertEquals(ConnectStatus.REFUSED, state.status)
        assertTrue(state.isTimeoutError)
    }

    @Test
    fun `connection refused sets REFUSED with isTimeoutError false`() {
        viewModel.attemptConnect("192.168.1.20", "8010")
        fakeRepo.emitStatus(ClientMessageCallback.Status.NO_CONNECT)

        fakeRepo.emitException(Exception("Connection refused"))
        val state = viewModel.uiState.value!!
        assertEquals(ConnectStatus.REFUSED, state.status)
        assertFalse(state.isTimeoutError)
    }

    @Test
    fun `onConnectionRefusedExtra disconnects and shows REFUSED`() {
        viewModel.onConnectionRefusedExtra()
        assertTrue(fakeRepo.disconnectCalled)
        assertEquals(ConnectStatus.REFUSED, viewModel.uiState.value!!.status)
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

    private class FakeMainRepository : IMainRepository {
        var callback: ClientMessageCallback? = null
        var lastConnectedHost: String? = null
        var lastConnectedPort: Int? = null
        var connected: Boolean = false
        var disconnectCalled: Boolean = false

        override fun getRecentAddresses(maxCount: Int): List<String> {
            return listOf("192.168.1.100", "192.168.1.101")
        }

        override fun getInitialHost(): String? = "192.168.1.100"

        override fun getInitialPort(): String? = "8010"

        override fun connect(host: String, port: Int) {
            lastConnectedHost = host
            lastConnectedPort = port
        }

        override fun disconnect() {
            disconnectCalled = true
            connected = false
        }

        override fun isConnected(): Boolean = connected

        override fun addCallback(callback: ClientMessageCallback) {
            this.callback = callback
        }

        override fun removeCallback(callback: ClientMessageCallback) {
            if (this.callback === callback) {
                this.callback = null
            }
        }

        fun emitStatus(status: ClientMessageCallback.Status) {
            callback?.onStatusChange(status)
        }

        fun emitException(e: Exception) {
            callback?.onExceptionToReOpen(e)
        }
    }
}
