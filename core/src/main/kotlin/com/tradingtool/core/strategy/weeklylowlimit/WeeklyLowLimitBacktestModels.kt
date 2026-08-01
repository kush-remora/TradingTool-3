package com.tradingtool.core.strategy.weeklylowlimit

import java.time.LocalDate

data class WeeklyLowLimitBacktestRequest(
    val mode: String = "STOCK",
    val entryRule: String = WeeklyLowLimitBacktestEntryRules.ANY_DAY_MAX_5_TRADING_DAYS,
    val symbol: String? = null,
    val instrumentToken: Long? = null,
    val watchlistKey: String? = null,
)

data class WeeklyLowLimitBacktestRunConfig(
    val mode: String,
    val entryRule: String,
    val symbol: String?,
    val instrumentToken: Long?,
    val watchlistKey: String?,
    val toDate: LocalDate,
)

data class WeeklyLowLimitBacktestTrade(
    val symbol: String,
    val instrumentToken: Long,
    val previousWeekStartDate: String,
    val entryWeekStartDate: String,
    val orderStartDate: String,
    val orderEndDate: String,
    val previousWeekLow: Double,
    val previousWeekLowDate: String,
    val previousWeekLastClose: Double,
    val limitPrice: Double,
    val outcome: String,
    val entryDate: String?,
    val entryOpenDeviationPct: Double?,
    val entryPrice: Double?,
    val stopPrice: Double?,
    val targetPrice: Double?,
    val exitDate: String?,
    val exitPrice: Double?,
    val holdingTradingDays: Int?,
    val returnPct: Double?,
    val gapFill: Boolean,
    val exitWasAmbiguous: Boolean,
)

data class WeeklyLowLimitDailyValidationRequest(
    val symbol: String,
    val instrumentToken: Long,
    val previousWeekLowDate: String,
    val entryWeekStartDate: String,
    val entryDate: String? = null,
)

data class WeeklyLowLimitDailyValidationRow(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val dailyChangePct: Double?,
)

data class WeeklyLowLimitDailyValidationResponse(
    val symbol: String,
    val previousWeekLowDate: String,
    val entryWeekStartDate: String,
    val entryDate: String?,
    val rows: List<WeeklyLowLimitDailyValidationRow>,
)

data class WeeklyLowLimitBacktestSummary(
    val setupCount: Int,
    val noFillCount: Int,
    val filledTradeCount: Int,
    val targetHitCount: Int,
    val stopLossCount: Int,
    val timeExitCount: Int,
    val positionOpenSkipCount: Int,
    val premarketFilterSkipCount: Int,
    val openDeviationSkipCount: Int,
    val ambiguousExitCount: Int,
    val averageReturnPct: Double?,
)

data class WeeklyLowLimitBacktestSymbolReport(
    val symbol: String,
    val companyName: String?,
    val entryRule: String,
    val testedFromDate: String,
    val testedToDate: String,
    val summary: WeeklyLowLimitBacktestSummary,
    val trades: List<WeeklyLowLimitBacktestTrade>,
)

data class WeeklyLowLimitBacktestReport(
    val mode: String,
    val entryRule: String,
    val selection: String,
    val testedFromDate: String,
    val testedToDate: String,
    val summary: WeeklyLowLimitBacktestSummary,
    val symbols: List<WeeklyLowLimitBacktestSymbolReport>,
)

internal data class WeeklyLowLimitMember(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
)

object WeeklyLowLimitBacktestEntryRules {
    const val ANY_DAY_MAX_5_TRADING_DAYS = "ANY_DAY_MAX_5_TRADING_DAYS"
    const val FIRST_3_DAYS_WEEK_CLOSE = "FIRST_3_DAYS_WEEK_CLOSE"

    val all: Set<String> = setOf(ANY_DAY_MAX_5_TRADING_DAYS, FIRST_3_DAYS_WEEK_CLOSE)
}
