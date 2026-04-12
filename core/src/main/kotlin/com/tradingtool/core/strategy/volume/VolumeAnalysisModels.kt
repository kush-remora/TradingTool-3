package com.tradingtool.core.strategy.volume

import java.time.LocalDate

data class VolumeAnalysisResult(
    val asOfDate: LocalDate,
    val close: Double,
    val avgVolume20d: Double,
    val avgTradedValueCr20d: Double,
    val todayVolumeRatio: Double,
    val recent3dAvgVolumeRatio: Double,
    val recent5dMaxVolumeRatio: Double,
    val spikePersistenceDays5d: Int,
    val recent10dAvgVolumeRatio: Double,
    val elevatedVolumeDays10d: Int,
    val todayPriceChangePct: Double,
    val priceReturn3dPct: Double,
    val breakoutAbove20dHigh: Boolean,
)
