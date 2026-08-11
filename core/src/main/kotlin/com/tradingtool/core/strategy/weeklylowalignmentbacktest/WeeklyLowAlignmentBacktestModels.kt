package com.tradingtool.core.strategy.weeklylowalignmentbacktest

import java.time.LocalDate

data class WeeklyLowAlignmentBacktestRequest(
    val watchlistKey: String,
    val targetPct: Double = 5.0,
    val maxHoldingTradingDays: Int = 5,
)

data class WeeklyLowAlignmentBacktestRunConfig(
    val watchlistKey: String,
    val targetPct: Double,
    val maxHoldingTradingDays: Int,
    val toDate: LocalDate,
)

data class WeeklyLowAlignmentBacktestTrade(
    val symbol: String,
    val instrumentToken: Long,
    val previousWeekStartDate: String,
    val entryWeekStartDate: String,
    val previousWeekLow: Double,
    val previousWeekLowDate: String,
    val retestDate: String?,
    val retestLow: Double?,
    val retestGapTradingDays: Int?,
    val entryPrice: Double,
    val targetPrice: Double,
    val outcome: String,
    val entryDate: String?,
    val exitDate: String?,
    val exitPrice: Double?,
    val holdingTradingDays: Int?,
    val returnPct: Double?,
)

data class WeeklyLowAlignmentBacktestSummary(
    val setupCount: Int,
    val noRetestCount: Int,
    val tooSoonRetestCount: Int,
    val filledTradeCount: Int,
    val targetHitCount: Int,
    val timeExitCount: Int,
    val positionOpenSkipCount: Int,
    val averageReturnPct: Double?,
)

data class WeeklyLowAlignmentBacktestSymbolReport(
    val symbol: String,
    val companyName: String?,
    val testedFromDate: String,
    val testedToDate: String,
    val summary: WeeklyLowAlignmentBacktestSummary,
    val trades: List<WeeklyLowAlignmentBacktestTrade>,
)

data class WeeklyLowAlignmentBacktestReport(
    val watchlistKey: String,
    val testedFromDate: String,
    val testedToDate: String,
    val targetPct: Double,
    val maxHoldingTradingDays: Int,
    val minimumRetestGapTradingDays: Int,
    val retestTolerancePct: Double,
    val summary: WeeklyLowAlignmentBacktestSummary,
    val symbols: List<WeeklyLowAlignmentBacktestSymbolReport>,
)

object WeeklyLowAlignmentBacktestOutcomes {
    const val NO_RETEST = "NO_RETEST"
    const val TOO_SOON_RETEST = "TOO_SOON_RETEST"
    const val POSITION_OPEN_SKIP = "POSITION_OPEN_SKIP"
    const val TARGET_HIT = "TARGET_HIT"
    const val TIME_EXIT = "TIME_EXIT"
}

internal data class WeeklyLowAlignmentMember(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
)
