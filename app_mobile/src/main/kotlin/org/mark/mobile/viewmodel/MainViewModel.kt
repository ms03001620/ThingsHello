package org.mark.mobile.viewmodel

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.mark.lib_unit_socket.ClientMessageCallback
import org.mark.lib_unit_socket.bean.JsonReceiver
import org.mark.mobile.repository.ICarControlRepository
import org.mark.mobile.repository.ICarPrefsRepository
import org.mark.mobile.usecase.ConnectCarUseCase
import org.mark.mobile.utils.AndroidUiScheduler
import org.mark.mobile.utils.UiScheduler

/**
 * MainActivity 对应的 MVVM ViewModel，负责管理连接状态机、最近地址加载与导航事件
 */
class MainViewModel(
    private val controlRepository: ICarControlRepository,
    private val prefsRepository: ICarPrefsRepository,
    private val connectCarUseCase: ConnectCarUseCase = ConnectCarUseCase(controlRepository),
    private val scheduler: UiScheduler = AndroidUiScheduler()
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
        private const val NAVIGATE_DELAY_MS = 350L
    }

    val uiState = MutableLiveData(MainUiState())
    val recentAddresses = MutableLiveData<List<String>>(emptyList())
    val initialInput = MutableLiveData<Pair<String?, String?>>()
    val navigateToCtrl = MutableLiveData<Event<Pair<String, Int>>>()

    private var connectRequested = false
    private var userCancelled = false

    @Volatile
    private var lastErrorMessage = ""

    private var currentHost: String = ""
    private var currentPort: Int = 8010

    private val clientMessageCallback = object : JsonReceiver() {
        override fun onReceiverJson(json: String?, type: Int) {
            Log.d(TAG, "onReceiverJson type:$type, json:$json")
        }

        override fun onExceptionToReOpen(e: Exception) {
            val err = e.message ?: e.toString()
            lastErrorMessage = err
            scheduler.runOnUi {
                lastErrorMessage = err
                val currentStatus = uiState.value?.status ?: ConnectStatus.IDLE
                if ((connectRequested && !userCancelled) || currentStatus == ConnectStatus.REFUSED) {
                    connectRequested = false
                    updateStatus(ConnectStatus.REFUSED)
                } else {
                    updateStatus(ConnectStatus.IDLE)
                }
            }
        }

        override fun onLogMessage(message: String?, e: Exception?) {
            if (e != null) {
                val err = e.message ?: e.toString()
                lastErrorMessage = err
                scheduler.runOnUi {
                    lastErrorMessage = err
                    val current = uiState.value
                    if (current?.status == ConnectStatus.REFUSED) {
                        val isTimeout = connectCarUseCase.isTimeoutError(err)
                        if (current.isTimeoutError != isTimeout) {
                            uiState.value = current.copy(isTimeoutError = isTimeout)
                        }
                    }
                }
                runCatching { Log.e(TAG, message, e) }
            } else if (message != null) {
                runCatching { Log.d(TAG, message) }
            }
        }

        override fun onStatusChange(status: ClientMessageCallback.Status) {
            scheduler.runOnUi {
                handleSocketStatus(status)
            }
        }
    }

    init {
        controlRepository.addCallback(clientMessageCallback)
        initialInput.value = Pair(prefsRepository.getInitialHost(), prefsRepository.getInitialPort())
        refreshRecentAddresses()
    }

    fun setUiState(newState: MainUiState) {
        uiState.value = newState
    }

    fun refreshRecentAddresses() {
        recentAddresses.value = prefsRepository.getRecentAddresses()
    }

    fun onHostTextChanged() {
        val current = uiState.value ?: return
        if (current.hasInputError) {
            uiState.value = current.copy(hasInputError = false)
        }
    }

    /**
     * 处理点击主连接按钮
     * @return true 表示发起了新的连接尝试（View 层可据此收起软键盘）
     */
    fun onConnectClicked(hostInput: String, portInput: String): Boolean {
        return when (uiState.value?.status) {
            ConnectStatus.CONNECTING -> false
            ConnectStatus.ONLINE -> {
                val trimmedHost = hostInput.trim()
                val trimmedPort = portInput.trim().toIntOrNull()
                if (controlRepository.isConnected() &&
                    trimmedHost.isNotEmpty() &&
                    trimmedHost == controlRepository.getConnectedHost() &&
                    trimmedPort != null &&
                    trimmedPort == controlRepository.getConnectedPort()
                ) {
                    connectRequested = false
                    navigateToCtrl.value = Event(Pair(trimmedHost, trimmedPort))
                    false
                } else {
                    attemptConnect(hostInput, portInput)
                    true
                }
            }
            else -> {
                attemptConnect(hostInput, portInput)
                true
            }
        }
    }

    fun attemptConnect(hostInput: String, portInput: String) {
        val trimmedHost = hostInput.trim()
        val trimmedPort = portInput.trim().toIntOrNull()
        if (trimmedHost.isNotEmpty() && trimmedPort != null && trimmedPort in 1..65535) {
            // 在 connect() 触发任何同步回调前先准备好连接上下文状态
            currentHost = trimmedHost
            currentPort = trimmedPort
            userCancelled = false
            connectRequested = true
            lastErrorMessage = ""
            uiState.value = MainUiState(
                status = ConnectStatus.CONNECTING,
                isTimeoutError = false,
                hasInputError = false,
                highlightHostError = false,
                connectedPort = portInput.trim()
            )
        }

        when (val result = connectCarUseCase(hostInput, portInput)) {
            is ConnectCarUseCase.Result.InvalidInput -> {
                val current = uiState.value ?: MainUiState()
                uiState.value = current.copy(
                    hasInputError = true,
                    highlightHostError = result.highlightHost
                )
            }
            is ConnectCarUseCase.Result.Started -> Unit
        }
    }

    fun onConnectionRefusedExtra() {
        connectRequested = false
        lastErrorMessage = ""
        controlRepository.disconnect()
        updateStatus(ConnectStatus.REFUSED)
    }

    fun forceDebugState(stateName: String, portInput: String) {
        try {
            val target = ConnectStatus.valueOf(stateName.uppercase())
            val portText = portInput.trim().ifEmpty { "8010" }
            val current = uiState.value ?: MainUiState()
            uiState.value = current.copy(
                status = target,
                isTimeoutError = connectCarUseCase.isTimeoutError(lastErrorMessage),
                connectedPort = portText
            )
        } catch (_: IllegalArgumentException) {
        }
    }

    fun onActivityFinishing() {
        userCancelled = true
        controlRepository.disconnect()
    }

    private fun handleSocketStatus(status: ClientMessageCallback.Status) {
        val currentStatus = uiState.value?.status ?: ConnectStatus.IDLE
        when (status) {
            ClientMessageCallback.Status.CONNECTING -> {
                if (connectRequested) {
                    updateStatus(ConnectStatus.CONNECTING)
                }
            }
            ClientMessageCallback.Status.CONNECTED -> {
                updateStatus(ConnectStatus.ONLINE)
                scheduler.postDelayed(NAVIGATE_DELAY_MS) {
                    if (uiState.value?.status == ConnectStatus.ONLINE && controlRepository.isConnected()) {
                        connectRequested = false
                        navigateToCtrl.value = Event(Pair(currentHost, currentPort))
                    }
                }
            }
            ClientMessageCallback.Status.NO_CONNECT -> {
                if (currentStatus == ConnectStatus.REFUSED) {
                    return
                }
                if (userCancelled) {
                    updateStatus(ConnectStatus.IDLE)
                } else if (connectRequested) {
                    connectRequested = false
                    updateStatus(ConnectStatus.REFUSED)
                } else {
                    updateStatus(ConnectStatus.IDLE)
                }
            }
        }
    }

    private fun updateStatus(newStatus: ConnectStatus) {
        val current = uiState.value ?: MainUiState()
        uiState.value = current.copy(
            status = newStatus,
            isTimeoutError = connectCarUseCase.isTimeoutError(lastErrorMessage)
        )
    }

    override fun onCleared() {
        scheduler.cancelAll()
        controlRepository.removeCallback(clientMessageCallback)
        super.onCleared()
    }

    class Factory(
        private val controlRepository: ICarControlRepository,
        private val prefsRepository: ICarPrefsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(controlRepository, prefsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
