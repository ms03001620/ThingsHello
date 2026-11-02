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
import org.mark.carpi.model.ControlEvent
import org.mark.carpi.repository.CommandRepository
import org.mark.carpi.repository.linux.HardwareConfig
import org.mark.carpi.repository.linux.LgpioClient
import org.mark.carpi.repository.linux.LinuxCarRepository
import org.mark.carpi.repository.JsonlActionLogRecorder
import org.mark.carpi.repository.LoggingCarRepository
import org.mark.carpi.repository.simulated.SimulatedCarRepository
import java.util.concurrent.atomic.AtomicBoolean

/** 控制服务：收集 CommandRepository 的事件，逐条交给 CarViewModel；控制端断开时停车。 */
class CarRuntime(config: CarConfig) : AutoCloseable {
    private val commands = CommandRepository(config.bind, config.port)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val viewModel: CarViewModel
    private val closed = AtomicBoolean()
    private val hardwareConfig: HardwareConfig = config.hardware

    init {
        viewModel = try {
            CarViewModel.of(hardware(config))
        } catch (e: Exception) {
            commands.close()
            throw e
        }
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

    val port: Int get() = commands.port

    fun start() {
        scope.launch {
            commands.events().collect { event ->
                when (event) {
                    ControlEvent.Connected -> Unit
                    is ControlEvent.Received -> try {
                        viewModel.handle(event.command)
                    } catch (e: Exception) {
                        System.err.println("handle: $e")
                    }
                    ControlEvent.Disconnected -> try {
                        withTimeout(3_000) { viewModel.releaseControl() }
                    } catch (e: Exception) {
                        System.err.println("disconnect stop: $e")
                    }
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        commands.close()
        runBlocking {
            withTimeoutOrNull(5_000) { scope.coroutineContext.job.children.toList().joinAll() }
        }
        scope.cancel()
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
