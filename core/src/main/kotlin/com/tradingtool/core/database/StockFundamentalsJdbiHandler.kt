package com.tradingtool.core.database

import com.tradingtool.core.fundamentals.dao.StockFundamentalsReadDao
import com.tradingtool.core.fundamentals.dao.StockFundamentalsWriteDao

typealias StockFundamentalsJdbiHandler = JdbiHandler<StockFundamentalsReadDao, StockFundamentalsWriteDao>
