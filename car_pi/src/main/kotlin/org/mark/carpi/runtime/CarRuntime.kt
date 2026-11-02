package org.mark.carpi.runtime

import org.mark.carpi.CarConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.mark.carpi.viewmodel.CarViewModel
import org.mark.carpi.repository.CommandRepository
import org.mark.carpi.repository.linux.HardwareConfig
import org.mark.carpi.repository.linux.LgpioClient
import org.mark.carpi.repository.linux.LinuxCarRepository
import org.mark.carpi.repository.JsonlActionLogRecorder
import org.mark.carpi.repository.LoggingCarRepository
import org.mark.carpi.repository.simulated.SimulatedCarRepository
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** TCP 控制服务：单控制端连接，收集 CommandRepository 的 Flow，逐条交给 CarViewModel（处理完再读下一帧）。 */
class CarRuntime(config: CarConfig) : AutoCloseable {
    private val server = ServerSocket()
    private val clients: MutableSet<Socket> = ConcurrentHashMap.newKeySet()
    /** accept 与客户端读循环（阻塞 socket IO，跑在 Dispatchers.IO 上）。 */
    private val workers = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val viewModel: CarViewModel
    private val closed = AtomicBoolean()
    private val hardwareConfig: HardwareConfig = config.hardware

    init {
        var created: CarViewModel? = null
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(config.bind, config.port))
            created = CarViewModel.of(hardware(config))
        } catch (e: Exception) {
            server.close()
            created?.close()
            throw e
        }
        viewModel = created
    }

    /** 启动成功（READY 之后）蜂鸣提示；buzzer.startupMs=0 关闭，real 模式未启用蜂鸣器则跳过，失败只告警。 */
    fun startupBeep() {
        val ms = hardwareConfig.startupBeepMs
        if (ms <= 0 || closed.get() || (hardwareConfig.real && hardwareConfig.buzzer == null)) return
        try {
            runBlocking { viewModel.beep(ms) }
        } catch (e: RuntimeException) {
            System.err.println("WARN startup beep failed: $e")
        }
    }

    val port: Int get() = server.localPort

    fun start() {
        workers.launch { accept() }
    }

    private fun accept() {
        while (!closed.get()) {
            try {
                val socket = server.accept()
                synchronized(clients) {
                    if (closed.get()) { socket.close(); return }
                    // Old phone is the single controller; bound resources for idle clients.
                    if (clients.isNotEmpty()) { socket.close(); return@synchronized }
                    clients.add(socket)
                    workers.launch { serve(socket) }
                }
            } catch (e: IOException) {
                if (!closed.get()) System.err.println("accept: $e")
            }
        }
    }

    private suspend fun serve(socket: Socket) {
        try {
            socket.use {
                socket.tcpNoDelay = true
                // 无 buffer：handle 返回后才读下一帧，保持顺序并限制排队。
                CommandRepository(socket).commands().collect { viewModel.handle(it) }
            }
        } catch (e: Exception) {
            if (!closed.get()) System.err.println("client: $e")
        } finally {
            if (!closed.get()) {
                try {
                    withTimeout(3_000) { viewModel.releaseControl() }
                } catch (e: Exception) {
                    System.err.println("disconnect stop: $e")
                }
            }
            clients.remove(socket)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try { server.close() } catch (e: IOException) { System.err.println(e) }
        synchronized(clients) {
            for (s in clients) try { s.close() } catch (e: IOException) { System.err.println(e) }
        }
        runBlocking {
            withTimeoutOrNull(5_000) { workers.coroutineContext.job.children.toList().joinAll() }
        }
        workers.cancel()
        viewModel.close()
    }

    private companion object {
        fun hardware(config: CarConfig): LoggingCarRepository {
            val hw = config.hardware
            if (!hw.real) return SimulatedCarRepository.logged(config.actions)
            val recorder = JsonlActionLogRecorder(config.actions, hw.mode.key)
            try {
                return LoggingCarRepository(LinuxCarRepository(LgpioClient(hw), hw, recorder::record), recorder)
            } catch (e: Exception) {
                recorder.close()
                throw e
            }
        }
    }
}
