package org.mark.carpi

import kotlinx.coroutines.runBlocking
import org.mark.carpi.protocol.CommandParser
import org.mark.carpi.protocol.FrameCodec.Frame
import org.mark.carpi.session.CarSession

/** 测试用：同步调用 CarSession 的挂起 API（等价于原先 submit(...).get() 等 Future 等待）。 */
fun CarSession.send(type: Int, body: String) = runBlocking { handle(CommandParser.received(Frame(type, body))) }
fun CarSession.beepBlocking(ms: Int) = runBlocking { beep(ms) }
fun CarSession.releaseBlocking() = runBlocking { releaseControl() }
