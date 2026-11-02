package org.mark.carpi

import org.mark.carpi.model.Cancellable
import org.mark.carpi.model.Scheduler

/** 测试用虚拟时钟：advance 时按时间顺序在当前线程执行到期且未取消的任务。 */
class FakeScheduler : Scheduler {
    private class Task(val at: Long, val run: () -> Unit) { var cancelled = false }
    private val tasks = mutableListOf<Task>()
    var now = 0L
    override fun schedule(delayMs: Long, task: () -> Unit): Cancellable {
        val t = Task(now + delayMs, task); tasks += t
        return Cancellable { t.cancelled = true }
    }
    fun advance(ms: Long) {
        val target = now + ms
        while (true) {
            val next = tasks.filter { !it.cancelled && it.at <= target }.minByOrNull { it.at } ?: break
            tasks -= next; now = next.at; next.run()
        }
        now = target
    }
}
