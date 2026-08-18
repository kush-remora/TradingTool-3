package com.tradingtool.core.strategy.adaptivebreakout

data class AdaptiveBreakoutBacktestRequest(
    val symbol: String? = null,
    val instrumentToken: Long? = null,
    val watchlistKey: String? = null,
    val months: Long = 6,
    val targetPct: Double = 5.0,
    val stopLossPct: Double = 5.0,
)

enum class AdaptiveBreakoutBacktestExitReason {
    TARGET_HIT,
    STOP_LOSS,
    STOP_LOSS_SAME_CANDLE,
    END_OF_TEST,
}

data class AdaptiveBreakoutBacktestTrade(
    val symbol: String,
    val breakoutDate: String,
    val breakoutClose: Double,
    val entryDate: String,
    val entryPrice: Double,
    val targetPrice: Double,
    val stopPrice: Double,
    val exitDate: String,
    val exitPrice: Double,
    val exitReason: AdaptiveBreakoutBacktestExitReason,
    val holdingSessions: Int,
    val returnPct: Double,
    val ambiguousSameCandle: Boolean,
)

data class AdaptiveBreakoutBacktestSummary(
    val freshBreakoutCount: Int,
    val enteredTradeCount: Int,
    val targetHitCount: Int,
    val stopLossCount: Int,
    val endOfTestCount: Int,
    val winRatePct: Double?,
    val averageHoldingSessions: Double?,
)

data class AdaptiveBreakoutBacktestResponse(
    val symbol: String?,
    val watchlistKey: String? = null,
    val testedFromDate: String,
    val testedToDate: String,
    val targetPct: Double,
    val stopLossPct: Double,
    val entryRule: String,
    val ambiguousCandleRule: String,
    val summary: AdaptiveBreakoutBacktestSummary,
    val trades: List<AdaptiveBreakoutBacktestTrade>,
    val symbols: List<AdaptiveBreakoutBacktestSymbolReport> = emptyList(),
)

data class AdaptiveBreakoutBacktestSymbolReport(
    val symbol: String,
    val companyName: String?,
    val testedFromDate: String,
    val testedToDate: String,
    val summary: AdaptiveBreakoutBacktestSummary,
    val trades: List<AdaptiveBreakoutBacktestTrade>,
)
