package org.mark.mobile.usecase

import org.mark.mobile.repository.ICarControlRepository

/**
 * 校验主机地址与端口范围并发起 TCP 连接，同时识别连接超时/网络不可达错误类型。
 */
class ConnectCarUseCase(
    private val controlRepository: ICarControlRepository
) {
    sealed class Result {
        data class InvalidInput(val highlightHost: Boolean) : Result()
        data class Started(val host: String, val port: Int, val portText: String) : Result()
    }

    operator fun invoke(hostInput: String, portInput: String): Result {
        val host = hostInput.trim()
        val portText = portInput.trim()
        if (host.isEmpty() || portText.isEmpty()) {
            return Result.InvalidInput(highlightHost = true)
        }
        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            return Result.InvalidInput(highlightHost = false)
        }
        controlRepository.connect(host, port)
        return Result.Started(host = host, port = port, portText = portText)
    }

    fun isTimeoutError(msg: String?): Boolean {
        if (msg.isNullOrEmpty()) return false
        val lower = msg.lowercase()
        return lower.contains("timed out")
            || lower.contains("timeout")
            || lower.contains("enotunreach")
            || lower.contains("network is unreachable")
            || lower.contains("no route")
    }
}
