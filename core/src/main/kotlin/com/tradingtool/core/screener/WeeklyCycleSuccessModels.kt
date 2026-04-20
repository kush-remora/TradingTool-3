package com.tradingtool.core.screener

data class WeeklyCycleSuccessScanResponse(
    val runAt: String,
    val universe: String,
    val weeksRequested: Int,
    val weeksEvaluated: Int,
    val highLowThresholdPct: Double,
    val rocThresholdPct: Double,
    val stableBaseMaxDriftPct: Double,
    val results: List<WeeklyCycleSuccessRow>,
)

data class WeeklyCycleSuccessRow(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val universeBuckets: List<String>,
    val successCount: Int,
    val cycleCount: Int,
    val successRatePct: Double,
    val failedStartWeeks: List<String>,
    val lastCycleMetrics: WeeklyCycleMetrics?,
    val stableBasePass: Boolean,
    val stableBaseReason: String?,
    val stableBaseDriftPct: Double?,
    val stableBaseLowMin: Double?,
    val stableBaseLowMax: Double?,
    val stableBaseWeeksCount: Int,
    val lastWeekMondayDipPct: Double?,
    val avg8wMondayDipPct: Double?,
    val mondayDipSamples8w: Int,
)

data class WeeklyCycleMetrics(
    val weekLabel: String,
    val startDay: String,
    val endDay: String,
    val startLow: Double,
    val endClose: Double,
    val weekHigh: Double,
    val highLowPct: Double,
    val rocPct: Double,
    val success: Boolean,
)
