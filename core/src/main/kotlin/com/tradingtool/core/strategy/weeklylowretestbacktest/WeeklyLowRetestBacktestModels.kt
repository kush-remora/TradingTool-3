package com.tradingtool.core.strategy.weeklylowretestbacktest

import java.time.LocalDate

data class WeeklyLowRetestBacktestRequest(
    val watchlistKey: String,
    val symbol: String? = null,
    val limitOffsetPct: Double = 0.5,
    val targetPct: Double = 5.0,
)

data class WeeklyLowRetestBacktestRunConfig(
    val watchlistKey: String,
    val symbol: String?,
    val limitOffsetPct: Double,
    val targetPct: Double,
    val toDate: LocalDate,
)

data class WeeklyLowRetestObservation(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
    val lookbackStartDate: String,
    val lookbackEndDate: String,
    val anchorDate: String,
    val anchorLow: Double,
    val anchorVolumeVs10DayAveragePct: Double?,
    val anchorCloseNearHighPct: Double?,
    val recentCycleLowDate: String,
    val recentCycleLow: Double,
    val triggerDate: String,
    val triggerHigh: Double,
    val triggerMovePct: Double,
    val cycleSequence: String,
    val limitOrderDate: String,
    val limitOrderExpiryDate: String,
    val limitPrice: Double,
    val orderWindowLowDate: String,
    val orderWindowLow: Double,
    val orderWindowLowVolumeVs10DayAveragePct: Double?,
    val orderWindowLowCloseNearHighPct: Double?,
    val fillDate: String?,
    val fillLow: Double?,
    val fillPrice: Double?,
    val fillVolumeVs10DayAveragePct: Double?,
    val fillCloseNearHighPct: Double?,
    val targetPrice: Double,
    val peakHighDate: String?,
    val peakHigh: Double?,
    val peakReturnPct: Double?,
    val fourthSessionCloseDate: String?,
    val fourthSessionClose: Double?,
    val noFillFourthSessionPnlPct: Double?,
    val targetReachedInOrderWindow: Boolean,
    val exitDate: String?,
    val exitPrice: Double?,
    val outcome: String,
    val realizedReturnPct: Double?,
    val holdingSessions: Int?,
)

data class WeeklyLowRetestBacktestSummary(
    val signalCount: Int,
    val noFillCount: Int,
    val filledTradeCount: Int,
    val targetHitCount: Int,
    val fourthSessionExitCount: Int,
    val profitableExitCount: Int,
    val lossExitCount: Int,
    val targetHitRatePct: Double?,
    val averageRealizedReturnPct: Double?,
    val medianRealizedReturnPct: Double?,
    val worstRealizedReturnPct: Double?,
    val totalRealizedReturnPct: Double?,
    val totalHoldingSessions: Int,
)

data class WeeklyLowRetestBacktestReport(
    val watchlistKey: String,
    val selectedSymbol: String?,
    val testedFromDate: String,
    val testedToDate: String,
    val limitOffsetPct: Double,
    val orderWindowSessions: Int,
    val targetPct: Double,
    val summary: WeeklyLowRetestBacktestSummary,
    val observations: List<WeeklyLowRetestObservation>,
)

object WeeklyLowRetestOutcomes {
    const val NO_FILL = "NO_FILL"
    const val TARGET_HIT = "TARGET_HIT"
    const val FOURTH_SESSION_EXIT = "FOURTH_SESSION_EXIT"
}

internal data class WeeklyLowRetestMember(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
)
