package org.mark.carpi.repository.datasource

import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/** 控制端的一次连接：读字节，用完关闭（关闭后数据源才接受下一个控制端）。 */
interface ControlConnection : AutoCloseable {
    val input: InputStream
}

/** 控制端字节数据源：每连上一个控制端发出一个 [ControlConnection]。真实实现是 TCP，测试可换成假的。 */
interface ControlDataSource : AutoCloseable {
    fun connections(): Flow<ControlConnection>
}
