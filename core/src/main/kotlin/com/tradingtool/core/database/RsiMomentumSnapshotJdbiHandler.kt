package com.tradingtool.core.database

import com.tradingtool.core.strategy.rsimomentum.dao.RsiMomentumSnapshotReadDao
import com.tradingtool.core.strategy.rsimomentum.dao.RsiMomentumSnapshotWriteDao

typealias RsiMomentumSnapshotJdbiHandler = JdbiHandler<RsiMomentumSnapshotReadDao, RsiMomentumSnapshotWriteDao>
