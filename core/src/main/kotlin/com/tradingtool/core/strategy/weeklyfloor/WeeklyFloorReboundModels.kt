package com.tradingtool.core.strategy.weeklyfloor

import java.time.LocalDate

data class WeeklyFloorReboundRequest(
    val symbol: String = "NETWEB",
)

data class WeeklyFloorReboundRunConfig(
    val symbol: String,
    val toDate: LocalDate,
    val backtestTradingDays: Int = 200,
)

data class WeeklyFloorReboundRow(
    val setupDate: String,
    val outcome: String,
    val eligibilityReason: String?,
    val baseFloor: Double?,
    val entryDate: String?,
    val entryPrice: Double?,
    val stopPrice: Double?,
    val targetPrice: Double?,
    val exitDate: String?,
    val exitPrice: Double?,
    val returnPct: Double?,
    val gapEntry: Boolean,
    val gapStop: Boolean,
    val exitWasAmbiguous: Boolean,
)

data class WeeklyFloorReboundSummary(
    val reviewedWeeks: Int,
    val eligibleSetups: Int,
    val filledTrades: Int,
    val noEntryCount: Int,
    val targetHitCount: Int,
    val stopLossCount: Int,
    val fridayExitCount: Int,
    val winRatePct: Double?,
    val averageReturnPct: Double?,
    val expectancyPct: Double?,
    val profitFactor: Double?,
    val maxDrawdownPct: Double?,
)

data class WeeklyFloorReboundReport(
    val symbol: String,
    val testedFromDate: String,
    val testedToDate: String,
    val summary: WeeklyFloorReboundSummary,
    val trades: List<WeeklyFloorReboundRow>,
)
