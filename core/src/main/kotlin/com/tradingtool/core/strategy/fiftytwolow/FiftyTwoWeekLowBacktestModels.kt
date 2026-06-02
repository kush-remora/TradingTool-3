package com.tradingtool.core.strategy.fiftytwolow

import java.time.LocalDate

data class FiftyTwoWeekLowBacktestRequest(
    val indexKeys: List<String> = emptyList(),
    val symbols: List<String> = emptyList(),
    val config: FiftyTwoWeekLowBacktestConfig = FiftyTwoWeekLowBacktestConfig(),
)

data class FiftyTwoWeekLowBacktestConfig(
    val profitPct: Double = 30.0,
    val historyDays: Long = 1300,
    val backtestDays: Long = 365,
    val toDate: String? = null,
)

data class FiftyTwoWeekLowBacktestRunConfig(
    val indexKeys: List<String>,
    val symbols: List<String>,
    val profitPct: Double,
    val historyDays: Long,
    val backtestDays: Long,
    val toDate: LocalDate,
)

data class FiftyTwoWeekLowBacktestRow(
    val symbol: String,
    val buyDate: String,
    val sellDate: String?,
    val daysHeld: Int,
    val profitPercentage: Double,
    val status: String,
)

data class FiftyTwoWeekLowBacktestSummary(
    val totalTrades: Int,
    val closedTrades: Int,
    val openTrades: Int,
)

data class FiftyTwoWeekLowBacktestConfigSnapshot(
    val indexKeys: List<String>,
    val symbols: List<String>,
    val profitPct: Double,
    val historyDays: Long,
    val backtestDays: Long,
    val fromDate: String,
    val toDate: String,
)

data class FiftyTwoWeekLowBacktestResponse(
    val config: FiftyTwoWeekLowBacktestConfigSnapshot,
    val summary: FiftyTwoWeekLowBacktestSummary,
    val rows: List<FiftyTwoWeekLowBacktestRow>,
)

data class FiftyTwoWeekLowResolvedSymbol(
    val symbol: String,
    val instrumentToken: Long,
    val memberships: List<String>,
)
