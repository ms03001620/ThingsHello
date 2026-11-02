package org.mark.carpi.golden

import org.mark.carpi.viewmodel.CarViewModel
import org.mark.carpi.send
import org.mark.carpi.repository.LoggingCarRepository

/** 测试用：以运行时相同的装配驱动 CarViewModel，逐帧同步执行（与 TCP 读循环一致：处理完一帧再读下一帧）。 */
class ViewModelDriver(repository: LoggingCarRepository) : AutoCloseable {
    private val viewModel = CarViewModel.of(repository)
    fun send(type: Int, body: String) { viewModel.send(type, body) }
    override fun close() = viewModel.close()
}
