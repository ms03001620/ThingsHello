package org.mark.carpi

import kotlinx.coroutines.runBlocking
import org.mark.carpi.repository.protocol.CommandParser
import org.mark.carpi.repository.protocol.FrameCodec.Frame
import org.mark.carpi.viewmodel.CarViewModel

/** 测试用：同步调用 CarViewModel 的挂起 API（等价于原先 submit(...).get() 等 Future 等待）。 */
fun CarViewModel.send(type: Int, body: String) = runBlocking { handle(CommandParser.received(Frame(type, body))) }
fun CarViewModel.beepBlocking(ms: Int) = runBlocking { beep(ms) }
fun CarViewModel.releaseBlocking() = runBlocking { releaseControl() }
