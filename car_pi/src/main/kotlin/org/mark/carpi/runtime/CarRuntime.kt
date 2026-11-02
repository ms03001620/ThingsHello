package org.mark.carpi.runtime

import org.mark.carpi.CarConfig
import kotlinx.coroutines.runBlocking
import org.mark.carpi.viewmodel.CarViewModel
import org.mark.carpi.repository.CommandRepository
import org.mark.carpi.repository.datasource.TcpControlDataSource
import org.mark.carpi.repository.linux.HardwareConfig
import org.mark.carpi.repository.linux.LgpioClient
import org.mark.carpi.repository.linux.LinuxCarRepository
import org.mark.carpi.repository.JsonlActionLogRecorder
import org.mark.carpi.repository.LoggingCarRepository
import org.mark.carpi.repository.simulated.SimulatedCarRepository
import java.util.concurrent.atomic.AtomicBoolean

/** 启动与装配（类似 Activity）：创建数据源与仓库交给 CarViewModel，start/close 管生命周期；不接触 socket 和数据流。 */
class CarRuntime(config: CarConfig) : AutoCloseable {
    private val source = TcpControlDataSource(config.bind, config.port)
    private val viewModel: CarViewModel
    private val closed = AtomicBoolean()
    private val hardwareConfig: HardwareConfig = config.hardware

    init {
        viewModel = try {
            CarViewModel.of(hardware(config), CommandRepository(source))
        } catch (e: Exception) {
            source.close()
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

    val port: Int get() = source.port

    fun start() = viewModel.start()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        source.close()
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
