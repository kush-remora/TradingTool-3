package com.tradingtool.core.database

import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao

typealias StockJdbiHandler = JdbiHandler<StockReadDao, StockWriteDao>
