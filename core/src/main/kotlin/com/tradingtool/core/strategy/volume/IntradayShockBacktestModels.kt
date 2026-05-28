package com.tradingtool.core.strategy.volume

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class IntradayShockBacktestRequest(
    val fromDate: String? = null,
    val toDate: String? = null,
    val universe: String? = null,
    val manualSymbols: List<String>? = null,
    val scanEndMinutes: Int? = 15,
    val entryDelayMinutes: Int? = 30,
    val gapUpTolerancePct: Double? = 5.0,
    val targetPct: Double? = 5.0,
    val hardStopPct: Double? = 3.0,
    val minTurnover: Double? = 5_000_000.0,
    val minVolumeSma: Double? = 100_000.0,
    val positionSizeInr: Double? = 50_000.0,
    val feePerTradeInr: Double? = 40.0,
)

data class IntradayShockRunConfig(
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val universe: String?,
    val manualSymbols: List<String>,
    val scanEndMinutes: Int,
    val entryDelayMinutes: Int,
    val gapUpTolerancePct: Double,
    val targetPct: Double,
    val hardStopPct: Double,
    val minTurnover: Double,
    val minVolumeSma: Double,
    val positionSizeInr: Double,
    val feePerTradeInr: Double,
    val exitTime: java.time.LocalTime,
)

data class IntradayShockBacktestTrade(
    val symbol: String,
    val instrumentToken: Long,
    val signalTime: String,
    val entryTime: String,
    val exitTime: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Int,
    val investedAmount: Double,
    val targetPrice: Double,
    val stopPrice: Double,
    val morningVolume: Double,
    val maxDailyVolume60d: Double,
    val maxDailyVolume63d: Double,
    val maxDailyVolume126d: Double,
    val maxDailyVolume252d: Double,
    val gapPct: Double,
    val exitReason: String,
    val grossPnlInr: Double,
    val feeInr: Double,
    val netPnlInr: Double,
    val netReturnPct: Double,
)

data class IntradayShockBacktestSummary(
    val symbolsConsidered: Int,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePct: Double,
    val grossPnlInr: Double,
    val totalFeesInr: Double,
    val netPnlInr: Double,
    val avgNetReturnPct: Double,
    val maxDrawdownInr: Double,
)

data class IntradayShockBacktestConfigSnapshot(
    val fromDate: String,
    val toDate: String,
    val universe: String?,
    val manualSymbols: List<String>,
    val scanEndMinutes: Int,
    val entryDelayMinutes: Int,
    val gapUpTolerancePct: Double,
    val targetPct: Double,
    val hardStopPct: Double,
)

data class IntradayShockBacktestDiagnostics(
    val symbolsConsidered: Int,
    val symbolsWithResolvedToken: Int,
    val symbolsWithNoToken: List<String>,
    val symbolsWithNoIntradayData: List<String>,
    val symbolsWithNoTrades: List<String>,
    val cacheHits: Int,
    val cacheMisses: Int,
    val kiteFetchFailures: List<String>,
)

data class IntradayShockBacktestResponse(
    val config: IntradayShockBacktestConfigSnapshot,
    val summary: IntradayShockBacktestSummary,
    val diagnostics: IntradayShockBacktestDiagnostics,
    val trades: List<IntradayShockBacktestTrade>,
)
