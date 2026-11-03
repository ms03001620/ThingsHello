package org.mark.mobile.utils

import android.os.Handler
import android.os.Looper

/**
 * 主线程调度器接口，默认使用 Android Main Looper，单元测试可注入虚拟时钟/同步实现
 */
interface UiScheduler {
    fun runOnUi(block: () -> Unit)
    fun postDelayed(delayMillis: Long, block: () -> Unit)

    fun scheduleCancellable(delayMillis: Long, block: () -> Unit): () -> Unit {
        var cancelled = false
        postDelayed(delayMillis) {
            if (!cancelled) {
                block()
            }
        }
        return { cancelled = true }
    }

    fun cancelAll()
}

class AndroidUiScheduler : UiScheduler {
    private val handler = Handler(Looper.getMainLooper())

    override fun runOnUi(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            handler.post(block)
        }
    }

    override fun postDelayed(delayMillis: Long, block: () -> Unit) {
        handler.postDelayed(block, delayMillis)
    }

    override fun scheduleCancellable(delayMillis: Long, block: () -> Unit): () -> Unit {
        val runnable = Runnable { block() }
        handler.postDelayed(runnable, delayMillis)
        return { handler.removeCallbacks(runnable) }
    }

    override fun cancelAll() {
        handler.removeCallbacksAndMessages(null)
    }
}
