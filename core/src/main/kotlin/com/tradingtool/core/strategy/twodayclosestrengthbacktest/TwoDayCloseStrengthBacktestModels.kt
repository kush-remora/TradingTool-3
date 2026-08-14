package com.tradingtool.core.strategy.twodayclosestrengthbacktest

import java.time.LocalDate

data class TwoDayCloseStrengthBacktestRequest(
    val watchlistKey: String,
)

data class TwoDayCloseStrengthBacktestRunConfig(
    val watchlistKey: String,
    val toDate: LocalDate,
)

data class TwoDayCloseStrengthObservation(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
    val patternStartDate: String,
    val patternEndDate: String,
    val patternClosePositionPct: List<Double>,
    val entryDate: String,
    val entryPrice: Double,
    val targetPrice: Double,
    val exitDate: String,
    val exitPrice: Double,
    val exitReason: String,
    val realizedReturnPct: Double,
)

data class TwoDayCloseStrengthBacktestSummary(
    val signalCount: Int,
    val targetHitCount: Int,
    val thursdayCloseExitCount: Int,
    val profitableExitCount: Int,
    val lossExitCount: Int,
    val averageRealizedReturnPct: Double?,
    val medianRealizedReturnPct: Double?,
    val worstRealizedReturnPct: Double?,
)

data class TwoDayCloseStrengthBacktestReport(
    val watchlistKey: String,
    val testedFromDate: String,
    val testedToDate: String,
    val closePositionThresholdPct: Double,
    val targetPct: Double,
    val summary: TwoDayCloseStrengthBacktestSummary,
    val observations: List<TwoDayCloseStrengthObservation>,
)

object TwoDayCloseStrengthExitReasons {
    const val TARGET_HIT = "TARGET_HIT"
    const val THURSDAY_CLOSE_EXIT = "THURSDAY_CLOSE_EXIT"
}

internal data class TwoDayCloseStrengthMember(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
)
