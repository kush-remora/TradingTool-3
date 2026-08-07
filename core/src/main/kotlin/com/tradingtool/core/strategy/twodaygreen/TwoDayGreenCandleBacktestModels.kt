package com.tradingtool.core.strategy.twodaygreen

data class TwoDayGreenCandleBacktestRequest(
    val watchlistKey: String? = null,
)

data class TwoDayGreenCandleObservation(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val dailyChangePct: Double?,
    val greenDay: Boolean,
    val openToClosePct: Double?,
    val lowToHighPct: Double?,
    val closeLocationPct: Double?,
)

data class TwoDayGreenCandleBacktestTrade(
    val symbol: String,
    val instrumentToken: Long,
    val setupDayOne: TwoDayGreenCandleObservation,
    val setupDayTwo: TwoDayGreenCandleObservation,
    val buyDay: TwoDayGreenCandleObservation,
    val setupVolumeRising: Boolean,
    val setupMoveRising: Boolean,
    val entryPrice: Double,
    val targetPrice: Double,
    val outcome: String,
    val exitDate: String?,
    val exitPrice: Double?,
    val holdingTradingDays: Int?,
    val maximumHighSinceEntryPct: Double,
    val unresolvedCloseReturnPct: Double?,
)

data class TwoDayGreenCandleBacktestSummary(
    val setupCount: Int,
    val targetHitCount: Int,
    val unresolvedCount: Int,
    val targetHitRatePct: Double?,
    val averageHoldingTradingDays: Double?,
)

data class TwoDayGreenCandleSymbolReport(
    val symbol: String,
    val companyName: String?,
    val testedFromDate: String,
    val testedToDate: String,
    val summary: TwoDayGreenCandleBacktestSummary,
    val trades: List<TwoDayGreenCandleBacktestTrade>,
)

data class TwoDayGreenCandleBacktestReport(
    val watchlistKey: String,
    val testedFromDate: String,
    val testedToDate: String,
    val summary: TwoDayGreenCandleBacktestSummary,
    val symbols: List<TwoDayGreenCandleSymbolReport>,
)

internal data class TwoDayGreenCandleMember(
    val symbol: String,
    val companyName: String?,
    val instrumentToken: Long,
)

object TwoDayGreenCandleOutcomes {
    const val TARGET_HIT = "TARGET_HIT"
    const val UNRESOLVED = "UNRESOLVED"
}
