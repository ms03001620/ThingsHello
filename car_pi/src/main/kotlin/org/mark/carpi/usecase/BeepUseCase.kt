package org.mark.carpi.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mark.carpi.repository.CarRepository

/** 蜂鸣：开启后 ms 毫秒以同一 id 关闭（手机指令 500ms；启动提示 id=0）。重复蜂鸣会重置截止时间。 */
class BeepUseCase(private val repository: CarRepository, private val timers: CoroutineScope) {
    private var stop: Job? = null

    operator fun invoke(id: Long, ms: Int = COMMAND_MS) {
        stop?.cancel()
        repository.buzzer(id, true)
        stop = timers.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(ms.toLong())
            repository.buzzer(id, false)
        }
    }

    fun cancel() { stop?.cancel() }

    companion object {
        const val COMMAND_MS = 500
    }
}
