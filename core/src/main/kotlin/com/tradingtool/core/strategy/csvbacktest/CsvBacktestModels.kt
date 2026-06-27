package com.tradingtool.core.strategy.csvbacktest

data class CsvBacktestApiRequest(
    val csvContent: String,
    val type: String,
    val targetPct: Double?,
    val stopLossPct: Double
)

data class CsvBacktestTradeResult(
    val symbol: String,
    val marketCapName: String,
    val sector: String,
    val signalDate: String,
    val entryDate: String?,
    val entryPrice: Double?,
    val exitDate: String?,
    val exitPrice: Double?,
    val profitLossPct: Double?,
    val daysHeld: Int,
    val slHit: Boolean,
    val isOpen: Boolean
)

data class CsvBacktestSummary(
    val month: String,
    val totalTrades: Int,
    val winTrades: Int,
    val lossTrades: Int,
    val avgHoldingPeriod: Double,
    val avgProfitPct: Double
)

data class CsvBacktestResponse(
    val trades: List<CsvBacktestTradeResult>,
    val summaries: List<CsvBacktestSummary>
)
