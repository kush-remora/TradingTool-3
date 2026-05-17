package com.tradingtool.core.strategy.bollinger

import kotlinx.serialization.Serializable

@Serializable
data class BollingerMeanReversionBacktestConfig(
    val capital: Double = 200_000.0,
    val maxOpenPositions: Int = 5,
    val fromDate: String? = null,
    val toDate: String? = null,
    val signalWindowDays: Int = 5,
    val volumeMultiplier: Double = 2.0,
    val bandwidthRecoveryThreshold: Double = 0.75,
    val maxHoldDays: Int = 999,
)

@Serializable
data class BollingerMeanReversionBacktestRequest(
    val universe: String = "WATCHLIST",
    val symbols: List<String> = emptyList(),
    val config: BollingerMeanReversionBacktestConfig = BollingerMeanReversionBacktestConfig(),
)

@Serializable
data class BollingerMeanReversionCriteriaSnapshot(
    val percentB: Double,
    val rsi14: Double?,
    val bandwidthPct: Double,
    val volumeRatio20: Double,
    val closeAboveSma200: Boolean?,
    val signal: String,
    val reasoning: String,
)

@Serializable
data class BollingerMeanReversionBacktestTrade(
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
    val entryCriteria: BollingerMeanReversionCriteriaSnapshot,
    val exitCriteria: BollingerMeanReversionCriteriaSnapshot,
    val debugRows: List<BollingerMeanReversionBacktestDebugRow>,
)

@Serializable
data class BollingerMeanReversionBacktestDebugRow(
    val date: String,
    val ltp: Double,
    val bbUpper: Double,
    val bbMiddle: Double,
    val bbLower: Double,
    val percentB: Double,
    val bandwidthPct: Double,
    val rsi14: Double?,
    val volumeRatio20: Double,
    val closeAboveSma200: Boolean?,
    val signal: String,
    val reasoning: String,
)

@Serializable
data class BollingerMeanReversionBacktestSummary(
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
data class BollingerMeanReversionBacktestDiagnostics(
    val symbolsConsidered: Int,
    val symbolsWithInsufficientData: List<String>,
    val symbolsWithNoTrades: List<String>,
)

@Serializable
data class BollingerMeanReversionBacktestConfigSnapshot(
    val universe: String,
    val capital: Double,
    val maxOpenPositions: Int,
    val fromDate: String?,
    val toDate: String?,
    val signalWindowDays: Int,
    val volumeMultiplier: Double,
    val bandwidthRecoveryThreshold: Double,
    val maxHoldDays: Int,
)

@Serializable
data class BollingerMeanReversionBacktestResponse(
    val config: BollingerMeanReversionBacktestConfigSnapshot,
    val summary: BollingerMeanReversionBacktestSummary,
    val diagnostics: BollingerMeanReversionBacktestDiagnostics,
    val trades: List<BollingerMeanReversionBacktestTrade>,
)
