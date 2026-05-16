package com.tradingtool.core.strategy.bollinger

import kotlinx.serialization.Serializable

@Serializable
data class BollingerBacktestConfig(
    val capital: Double = 200_000.0,
    val maxOpenPositions: Int = 5,
    val fromDate: String? = null,
    val toDate: String? = null,
    val signalWindowDays: Int = 5,
    val entryRsiMax: Double = 30.0,
    val takeProfitPct: Double = 5.0,
    val stopLossPct: Double = 2.0,
    val maxHoldDays: Int = 5,
)

@Serializable
data class BollingerBacktestRequest(
    val universe: String = "WATCHLIST",
    val symbols: List<String> = emptyList(),
    val config: BollingerBacktestConfig = BollingerBacktestConfig(),
)

@Serializable
data class BollingerCriteriaSnapshot(
    val percentB: Double,
    val rsi14: Double?,
    val bandwidthPct: Double,
    val setupScore: Int,
    val signal: String,
    val reasoning: String,
)

@Serializable
data class BollingerBacktestTrade(
    val symbol: String,
    val companyName: String,
    val entryDate: String,
    val exitDate: String,
    val holdingDays: Int,
    val quantity: Int,
    val investedAmount: Double,
    val entryPrice: Double,
    val exitPrice: Double,
    val exitReason: String,
    val grossPnlInr: Double,
    val netPnlInr: Double,
    val netReturnPct: Double,
    val entryCriteria: BollingerCriteriaSnapshot,
    val exitCriteria: BollingerCriteriaSnapshot,
    val debugRows: List<BollingerBacktestDebugRow>,
)

@Serializable
data class BollingerBacktestDebugRow(
    val date: String,
    val ltp: Double,
    val bbUpper: Double,
    val bbMiddle: Double,
    val bbLower: Double,
    val percentB: Double,
    val bandwidthPct: Double,
    val rsi14: Double?,
    val setupScore: Int,
    val signal: String,
    val reasoning: String,
)

@Serializable
data class BollingerBacktestSummary(
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePct: Double,
    val grossPnlInr: Double,
    val totalBrokerageInr: Double,
    val netPnlInr: Double,
    val totalReturnPct: Double,
    val avgReturnPerTradePct: Double,
    val maxDrawdownInr: Double,
    val finalCapital: Double,
)

@Serializable
data class BollingerBacktestDiagnostics(
    val symbolsConsidered: Int,
    val symbolsWithInsufficientData: List<String>,
    val symbolsWithNoTrades: List<String>,
)

@Serializable
data class BollingerBacktestConfigSnapshot(
    val universe: String,
    val capital: Double,
    val maxOpenPositions: Int,
    val fromDate: String?,
    val toDate: String?,
    val signalWindowDays: Int,
    val entryRsiMax: Double,
    val takeProfitPct: Double,
    val stopLossPct: Double,
    val maxHoldDays: Int,
)

@Serializable
data class BollingerBacktestResponse(
    val config: BollingerBacktestConfigSnapshot,
    val summary: BollingerBacktestSummary,
    val diagnostics: BollingerBacktestDiagnostics,
    val trades: List<BollingerBacktestTrade>,
)
