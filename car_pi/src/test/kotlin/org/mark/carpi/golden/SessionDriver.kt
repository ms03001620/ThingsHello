package org.mark.carpi.golden

import org.mark.carpi.protocol.FrameCodec.Frame
import org.mark.carpi.repository.LoggingCarRepository
import org.mark.carpi.runtime.carSession

/** 测试用：以运行时相同的装配驱动 CarSession，逐帧同步执行（与 TCP 读循环一致：处理完一帧再读下一帧）。 */
class SessionDriver(repository: LoggingCarRepository) : AutoCloseable {
    private val session = carSession(repository)
    fun send(type: Int, body: String) { session.submit(Frame(type, body)) {}.get() }
    override fun close() = session.close()
}
