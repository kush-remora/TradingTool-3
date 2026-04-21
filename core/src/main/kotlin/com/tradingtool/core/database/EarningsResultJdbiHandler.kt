package com.tradingtool.core.database

import com.tradingtool.core.earnings.dao.EarningsResultReadDao
import com.tradingtool.core.earnings.dao.EarningsResultWriteDao

typealias EarningsResultJdbiHandler = JdbiHandler<EarningsResultReadDao, EarningsResultWriteDao>
