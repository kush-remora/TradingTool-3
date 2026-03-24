package com.tradingtool.core.database

import com.tradingtool.core.strategy.remora.RemoraSignalReadDao
import com.tradingtool.core.strategy.remora.RemoraSignalWriteDao

typealias RemoraJdbiHandler = JdbiHandler<RemoraSignalReadDao, RemoraSignalWriteDao>
