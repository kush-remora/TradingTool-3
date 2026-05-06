package com.tradingtool.core.strategy.volume

import java.time.LocalDate
import java.time.LocalTime

enum class EarningsFilterMode {
    OFF,
    CUSTOM_WINDOW,
    MANUAL_SYMBOL,
}

data class VolumeSpikeBacktestRequest(
    val fromDate: String? = null,
    val toDate: String? = null,
    val delayMinutes: Int? = null,
    val manualSymbols: List<String> = emptyList(),
    val earningsFilterMode: EarningsFilterMode = EarningsFilterMode.OFF,
    val earningsWindowStartOffsetDays: Int? = null,
    val earningsWindowEndOffsetDays: Int? = null,
    val rvolThreshold: Double = 2.0,
    val minDayMoveFromOpenPct: Double = 3.0,
    val targetPct: Double = 5.0,
    val stopPct: Double = 2.0,
    val minThirtyMinReturnPct: Double = 1.0,
    val latestEntryTime: String? = "14:30",
    val buyerDominancePct: Double? = null,
    val positionSizeInr: Double = 200_000.0,
    val feePerTradeInr: Double = 500.0,
)

data class VolumeSpikeBacktestRunConfig(
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val delayMinutes: Int,
    val manualSymbols: List<String>,
    val earningsFilterMode: EarningsFilterMode,
    val earningsWindowStartOffsetDays: Int?,
    val earningsWindowEndOffsetDays: Int?,
    val rvolThreshold: Double,
    val minDayMoveFromOpenPct: Double,
    val targetPct: Double,
    val stopPct: Double,
    val minThirtyMinReturnPct: Double,
    val latestEntryTime: LocalTime,
    val buyerDominancePct: Double?,
    val positionSizeInr: Double,
    val feePerTradeInr: Double,
)

data class VolumeSpikeBacktestTrade(
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
    val rvolAtSignal: Double,
    val signalCandleVolume: Double,
    val avgSlotVolume20d: Double,
    val vwapAtSignal: Double,
    val prior30MinHigh: Double,
    val exitReason: String,
    val grossPnlInr: Double,
    val feeInr: Double,
    val netPnlInr: Double,
    val netReturnPct: Double,
)

data class VolumeSpikeBacktestSummary(
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

data class VolumeSpikeBacktestDiagnostics(
    val symbolsFromEarningsUniverse: Int,
    val symbolsFromManualInput: Int,
    val symbolsWithResolvedToken: Int,
    val symbolsWithNoToken: List<String>,
    val symbolsWithNoIntradayData: List<String>,
    val symbolsSkippedByEarningsFilter: List<String>,
    val symbolsWithNoTrades: List<String>,
    val cacheHits: Int,
    val cacheMisses: Int,
    val kiteFetchFailures: List<String>,
)

data class VolumeSpikeBacktestConfigSnapshot(
    val fromDate: String,
    val toDate: String,
    val delayMinutes: Int,
    val earningsFilterMode: EarningsFilterMode,
    val earningsWindowStartOffsetDays: Int?,
    val earningsWindowEndOffsetDays: Int?,
    val rvolThreshold: Double,
    val minDayMoveFromOpenPct: Double,
    val targetPct: Double,
    val stopPct: Double,
    val minThirtyMinReturnPct: Double,
    val latestEntryTime: String,
    val buyerDominancePct: Double?,
    val positionSizeInr: Double,
    val feePerTradeInr: Double,
)

data class VolumeSpikeBacktestResponse(
    val config: VolumeSpikeBacktestConfigSnapshot,
    val summary: VolumeSpikeBacktestSummary,
    val diagnostics: VolumeSpikeBacktestDiagnostics,
    val trades: List<VolumeSpikeBacktestTrade>,
)
