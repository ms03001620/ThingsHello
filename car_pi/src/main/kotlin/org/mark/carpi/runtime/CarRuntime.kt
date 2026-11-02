package org.mark.carpi.runtime

import org.mark.carpi.session.CarSession
import org.mark.carpi.repository.linux.HardwareConfig
import org.mark.carpi.repository.linux.LgpioClient
import org.mark.carpi.repository.linux.LinuxCarRepository
import org.mark.carpi.repository.JsonlActionLogRecorder
import org.mark.carpi.repository.LoggingCarRepository
import org.mark.carpi.repository.simulated.SimulatedCarRepository
import org.mark.carpi.protocol.FrameCodec
import java.io.IOException
import java.io.UncheckedIOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** TCP 控制服务：单控制端连接，按序把帧交给 CarSession。 */
class CarRuntime(config: CarConfig) : AutoCloseable {
    private val server = ServerSocket()
    private val clients: MutableSet<Socket> = ConcurrentHashMap.newKeySet()
    private val workers: ExecutorService = Executors.newCachedThreadPool()
    private val session: CarSession
    private val closed = AtomicBoolean()
    private val hardwareConfig: HardwareConfig = config.hardware

    init {
        var created: CarSession? = null
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(config.bind, config.port))
            created = carSession(hardware(config))
        } catch (e: Exception) {
            server.close()
            created?.close()
            throw e
        }
        session = created
    }

    /** 启动成功（READY 之后）蜂鸣提示；buzzer.startupMs=0 关闭，real 模式未启用蜂鸣器则跳过，失败只告警。 */
    fun startupBeep() {
        val ms = hardwareConfig.startupBeepMs
        if (ms <= 0 || closed.get() || (hardwareConfig.real && hardwareConfig.buzzer == null)) return
        try {
            session.beep(ms)
        } catch (e: RuntimeException) {
            System.err.println("WARN startup beep failed: $e")
        }
    }

    val port: Int get() = server.localPort

    fun start() {
        workers.submit(::accept)
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
                    workers.submit { read(socket) }
                }
            } catch (e: IOException) {
                if (!closed.get()) System.err.println("accept: $e")
            }
        }
    }

    private fun read(socket: Socket) {
        try {
            socket.use {
                socket.tcpNoDelay = true
                while (!closed.get()) {
                    val frame = FrameCodec.read(socket.getInputStream()) ?: break
                    // Await dispatch to preserve order and bound queued commands.
                    session.submit(frame) { reply ->
                        try {
                            socket.getOutputStream().write(FrameCodec.encode(reply.type, reply.body))
                        } catch (e: IOException) {
                            throw UncheckedIOException(e)
                        }
                    }.get()
                }
            }
        } catch (e: Exception) {
            if (!closed.get()) System.err.println("client: $e")
        } finally {
            if (!closed.get()) {
                try {
                    session.releaseControl().get(3, TimeUnit.SECONDS)
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
        workers.shutdown()
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) workers.shutdownNow()
        } catch (e: InterruptedException) {
            workers.shutdownNow()
            Thread.currentThread().interrupt()
        }
        session.close()
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
