package org.mark.carpi.runtime

import org.mark.carpi.repository.LoggingCarRepository
import org.mark.carpi.session.CarSession

/** 手工装配：同一个日志装饰器既是 CarRepository 也是事件 ActionLog。 */
fun carSession(repository: LoggingCarRepository) = CarSession(repository, repository)
