package org.mark.carpi.usecase

import org.mark.carpi.model.Cancellable
import org.mark.carpi.model.Scheduler
import org.mark.carpi.repository.CarRepository

/** 蜂鸣：开启后 ms 毫秒以同一 id 关闭（手机指令 500ms；启动提示 id=0）。重复蜂鸣会重置截止时间。 */
class BeepUseCase(private val repository: CarRepository, private val scheduler: Scheduler) {
    private var stop: Cancellable? = null

    operator fun invoke(id: Long, ms: Int = COMMAND_MS) {
        stop?.cancel()
        repository.buzzer(id, true)
        stop = scheduler.schedule(ms.toLong()) { repository.buzzer(id, false) }
    }

    fun cancel() { stop?.cancel() }

    companion object {
        const val COMMAND_MS = 500
    }
}
