package org.mark.mobile.ctrl

import android.os.SystemClock
import org.mark.mobile.utils.AndroidUiScheduler
import org.mark.mobile.utils.UiScheduler

/**
 * 左右轮目标差速（范围 -100..100）
 */
data class WheelSpeed(val left: Int, val right: Int)

/**
 * 按固定间隔最多发送一次最新值；间隔结束时冲刷尾部最新值；连续相同的值不重复发送。
 * 支持注入 [UiScheduler] 与 [clock] 以便纯 JVM 单元测试。
 */
class Throttle<T : Any>(
    private val intervalMs: Long,
    private val scheduler: UiScheduler = AndroidUiScheduler(),
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val sink: (T) -> Unit
) {
    private var lastSent: T? = null
    private var pending: T? = null
    private var lastTime: Long = -intervalMs
    private var cancelFlush: (() -> Unit)? = null

    fun submit(value: T) {
        if (value == lastSent && pending == null) return
        val now = clock()
        val wait = lastTime + intervalMs - now
        if (wait <= 0L) {
            cancelFlush?.invoke()
            cancelFlush = null
            pending = null
            emit(value)
        } else {
            pending = value
            cancelFlush?.invoke()
            cancelFlush = scheduler.scheduleCancellable(wait) {
                val next = pending
                pending = null
                cancelFlush = null
                if (next != null) {
                    emit(next)
                }
            }
        }
    }

    /** 立即发送（如松手急停），丢弃任何挂起的旧值 */
    fun sendNow(value: T) {
        cancelFlush?.invoke()
        cancelFlush = null
        pending = null
        emit(value)
    }

    fun cancel() {
        cancelFlush?.invoke()
        cancelFlush = null
        pending = null
    }

    /** 取消挂起任务并清空去重缓存（用于外部通道直接改变底层执行器状态时同步） */
    fun reset() {
        cancel()
        lastSent = null
    }

    private fun emit(value: T) {
        if (value == lastSent) return
        lastSent = value
        lastTime = clock()
        sink(value)
    }
}
