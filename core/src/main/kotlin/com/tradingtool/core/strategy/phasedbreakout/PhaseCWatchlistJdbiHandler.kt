package com.tradingtool.core.strategy.phasedbreakout

import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.strategy.phasedbreakout.dao.PhaseCWatchlistReadDao
import com.tradingtool.core.strategy.phasedbreakout.dao.PhaseCWatchlistWriteDao

typealias PhaseCWatchlistJdbiHandler =
    JdbiHandler<PhaseCWatchlistReadDao, PhaseCWatchlistWriteDao>
