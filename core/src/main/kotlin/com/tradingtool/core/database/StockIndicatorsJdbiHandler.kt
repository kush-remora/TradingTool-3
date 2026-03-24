package com.tradingtool.core.database

import com.tradingtool.core.stock.dao.StockIndicatorsReadDao
import com.tradingtool.core.stock.dao.StockIndicatorsWriteDao

typealias StockIndicatorsJdbiHandler = JdbiHandler<StockIndicatorsReadDao, StockIndicatorsWriteDao>
