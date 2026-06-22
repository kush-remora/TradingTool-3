package com.tradingtool.core.database

import com.tradingtool.core.volumeshocker.groww.dao.GrowwVolumeShockerReadDao
import com.tradingtool.core.volumeshocker.groww.dao.GrowwVolumeShockerWriteDao

typealias GrowwVolumeShockerJdbiHandler =
    JdbiHandler<GrowwVolumeShockerReadDao, GrowwVolumeShockerWriteDao>
