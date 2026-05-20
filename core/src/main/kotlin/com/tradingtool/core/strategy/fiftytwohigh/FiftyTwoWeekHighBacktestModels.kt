package com.tradingtool.core.strategy.fiftytwohigh

import java.time.LocalDate

data class FiftyTwoWeekHighBacktestRequest(
    val indexKeys: List<String> = emptyList(),
    val symbols: List<String> = emptyList(),
    val config: FiftyTwoWeekHighBacktestConfig = FiftyTwoWeekHighBacktestConfig(),
)

data class FiftyTwoWeekHighBacktestConfig(
    val profitPct: Double = 20.0,
    val historyDays: Long = 1300,
    val backtestDays: Long = 365,
    val cooldownDays: Long = 180,
    val toDate: String? = null,
)

data class FiftyTwoWeekHighBacktestRunConfig(
    val indexKeys: List<String>,
    val symbols: List<String>,
    val profitPct: Double,
    val historyDays: Long,
    val backtestDays: Long,
    val cooldownDays: Long,
    val toDate: LocalDate,
)

data class FiftyTwoWeekHighBacktestRow(
    val symbol: String,
    val indexBucket: String,
    val enterTrade: String,
    val exitTrade: String?,
    val holdingDays: Int,
    val status: String,
)

data class FiftyTwoWeekHighBacktestSummary(
    val totalTrades: Int,
    val closedTrades: Int,
    val openTrades: Int,
)

data class FiftyTwoWeekHighBacktestConfigSnapshot(
    val indexKeys: List<String>,
    val symbols: List<String>,
    val profitPct: Double,
    val historyDays: Long,
    val backtestDays: Long,
    val cooldownDays: Long,
    val fromDate: String,
    val toDate: String,
)

data class FiftyTwoWeekHighBacktestResponse(
    val config: FiftyTwoWeekHighBacktestConfigSnapshot,
    val summary: FiftyTwoWeekHighBacktestSummary,
    val rows: List<FiftyTwoWeekHighBacktestRow>,
)

data class FiftyTwoWeekHighResolvedSymbol(
    val symbol: String,
    val instrumentToken: Long,
    val memberships: List<String>,
)
