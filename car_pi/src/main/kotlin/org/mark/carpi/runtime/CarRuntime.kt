package org.mark.carpi.runtime

import kotlinx.coroutines.runBlocking
import org.mark.carpi.CarConfig
import org.mark.carpi.viewmodel.CarViewModel
import java.util.concurrent.atomic.AtomicBoolean

/** 启动入口（类似 Activity）：通过 CarViewModel.of(config) 拿到 ViewModel，只管 start / close 与启动蜂鸣。 */
class CarRuntime(config: CarConfig) : AutoCloseable {
    private val viewModel = CarViewModel.of(config)
    private val closed = AtomicBoolean()
    private val hardwareConfig = config.hardware

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

    val port: Int get() = viewModel.port

    fun start() = viewModel.start()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        viewModel.close()
    }
}
