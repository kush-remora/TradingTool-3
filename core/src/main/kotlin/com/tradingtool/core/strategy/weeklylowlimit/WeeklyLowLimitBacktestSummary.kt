package com.tradingtool.core.strategy.weeklylowlimit

internal fun summarizeWeeklyLowLimitTrades(
    trades: List<WeeklyLowLimitBacktestTrade>,
): WeeklyLowLimitBacktestSummary {
    val filledTrades = trades.filter { trade -> trade.outcome in FILLED_OUTCOMES }
    return WeeklyLowLimitBacktestSummary(
        setupCount = trades.size,
        noFillCount = trades.count { trade -> trade.outcome == "NO_FILL" },
        filledTradeCount = filledTrades.size,
        targetHitCount = trades.count { trade -> trade.outcome == "TARGET_HIT" },
        stopLossCount = trades.count { trade -> trade.outcome == "STOP_LOSS" },
        timeExitCount = trades.count { trade -> trade.outcome == "TIME_EXIT" },
        positionOpenSkipCount = trades.count { trade -> trade.outcome == "POSITION_OPEN_SKIP" },
        premarketFilterSkipCount = trades.count { trade -> trade.outcome == "PREMARKET_FILTER_SKIP" },
        openDeviationSkipCount = trades.count { trade -> trade.outcome == "OPEN_DEVIATION_SKIP" },
        ambiguousExitCount = trades.count(WeeklyLowLimitBacktestTrade::exitWasAmbiguous),
        averageReturnPct = filledTrades.mapNotNull(WeeklyLowLimitBacktestTrade::returnPct).averageOrNull(),
    )
}

private val FILLED_OUTCOMES = setOf("TARGET_HIT", "STOP_LOSS", "TIME_EXIT")

private fun List<Double>.averageOrNull(): Double? = takeIf(List<Double>::isNotEmpty)?.average()
