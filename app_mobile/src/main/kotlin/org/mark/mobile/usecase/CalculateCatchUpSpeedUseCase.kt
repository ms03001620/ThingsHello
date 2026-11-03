package org.mark.mobile.usecase

/**
 * RTSP 视频低延迟分层倍速追帧 + 45~75ms 回差防抖策略。
 */
class CalculateCatchUpSpeedUseCase {
    operator fun invoke(
        isReady: Boolean,
        bufferedMs: Long,
        currentAppliedSpeed: Float
    ): Float {
        if (!isReady) return 1.0f
        return when {
            bufferedMs > 350 -> 1.25f
            bufferedMs > 140 -> 1.15f
            bufferedMs > 75 -> 1.05f
            bufferedMs > 45 -> if (currentAppliedSpeed > 1.0f) 1.05f else 1.0f
            else -> 1.0f
        }
    }
}
