package com.tradingtool.core.strategy.rsimomentum.dao

import java.time.LocalDate
import java.time.OffsetDateTime

data class RsiMomentumSnapshotDailyRecord(
    val profileId: String,
    val asOfDate: LocalDate,
    val runAt: OffsetDateTime,
    val snapshotJson: String,
)
