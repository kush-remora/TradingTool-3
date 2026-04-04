package com.tradingtool.core.database

import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.candle.dao.CandleWriteDao

typealias CandleJdbiHandler = JdbiHandler<CandleReadDao, CandleWriteDao>
