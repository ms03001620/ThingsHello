package org.mark.carpi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** 测试用虚拟时钟：advance 时按时间顺序在当前线程执行到期且未取消的 delay 定时器（不创建线程）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeScheduler {
    private val scheduler = TestCoroutineScheduler()
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    val scope = CoroutineScope(dispatcher)
    val now: Long get() = scheduler.currentTime
    fun advance(ms: Long) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }
}
