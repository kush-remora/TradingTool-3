package com.tradingtool.core.database

import com.tradingtool.core.delivery.dao.StockDeliveryReadDao
import com.tradingtool.core.delivery.dao.StockDeliveryWriteDao

typealias StockDeliveryJdbiHandler = JdbiHandler<StockDeliveryReadDao, StockDeliveryWriteDao>
