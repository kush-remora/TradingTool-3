package com.tradingtool.core.strategy.trailingstopbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

data class TrailingStopSignal(
    val signalDate: LocalDate,
    val symbol: String,
    val marketCapName: String,
    val sector: String,
)

data class TrailingStopSymbolContext(
    val signal: TrailingStopSignal,
    val candles: List<DailyCandle>,
)

data class TrailingStopBacktestConfig(
    val inputFile: java.nio.file.Path,
    val priceDataToDate: LocalDate,
    val allocationPerTrade: Double = 10000.0,
)

data class TrailingStopBacktestApiRequest(
    val csvContent: String,
    val priceDataToDate: String?,
    val allocationPerTrade: Double?,
)

data class TrailingStopTradeRow(
    val symbol: String,
    val marketCapName: String,
    val sector: String,
    val signalDate: String,
    val entryDate: String?,
    val exitDate: String?,
    val entryPrice: Double?,
    val exitPrice: Double?,
    val shares: Int,
    val investedAmount: Double,
    val exitValue: Double,
    val profitLoss: Double,
    val profitLossPct: Double?,
    val holdingTradingDays: Int?,
    val outcome: String,
)

data class TrailingStopAggregateResult(
    val marketCapName: String,
    val sector: String,
    val totalTrades: Int,
    val profitableTrades: Int,
    val totalInvested: Double,
    val totalProfitLoss: Double,
    val averageReturnPct: Double?,
)

data class TrailingStopBacktestReport(
    val generatedAt: String,
    val inputFile: String,
    val priceDataToDate: String,
    val totalSignals: Int,
    val trades: List<TrailingStopTradeRow>,
    val aggregations: List<TrailingStopAggregateResult>,
)
