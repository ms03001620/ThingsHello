package org.mark.carpi.model

fun interface Cancellable {
    fun cancel()
}

/** 定时器抽象；实现必须与 CarController 的调用在同一线程串行执行任务。 */
fun interface Scheduler {
    fun schedule(delayMs: Long, task: () -> Unit): Cancellable
}
