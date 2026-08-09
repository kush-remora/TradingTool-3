package com.tradingtool.core.strategy.volumeeventbacktest

import com.tradingtool.core.candle.DailyCandle

data class VolumeEventConfirmationBacktestRequest(
    val watchlistKey: String? = null,
    val symbol: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val entryMode: String? = null,
)

data class VolumeEventConfirmationBacktestConfig(
    val volumeBaselineDays: Int = 5,
    val volumeShockMultiplier: Double = 2.0,
    val entryMode: String = VolumeEventEntryModes.FIVE_DAY_FUTURE_RSI_CONFIRMATION,
    val confirmationDays: Int = 5,
    val targetPct: Double = 5.0,
    val maxHoldingDays: Int = 15,
    val rsiPeriod: Int = 14,
    val maxLookbackDrawdownPct: Double = 5.0,
    val adaptiveRsiCalibrationDays: Int = 252,
    val adaptiveRsiBinSize: Double = 5.0,
    val adaptiveRsiMinimumSampleCount: Int = 8,
    val pastRsiLookbackDays: Int = 5,
)

data class VolumeEventConfirmationObservation(
    val symbol: String,
    val eventDate: String,
    val eventClose: Double,
    val eventVolume: Long,
    val priorVolumeAverage: Double,
    val volumeRatio: Double,
    val eventRsi: Double,
    val lookbackReturnPct: Double?,
    val lookbackDrawdownPct: Double?,
    val adaptiveRsiThreshold: Double?,
    val rsiCalibrationSampleCount: Int?,
    val rsiCalibrationBaselineHitRatePct: Double?,
    val rsiCalibrationSelectedHitRatePct: Double?,
    val pastRsiChangePoints: Double?,
    val pastRsiTrendPassed: Boolean?,
    val confirmationDate: String?,
    val confirmationRsi: Double?,
    val rsiChangePoints: Double?,
    val entryDate: String?,
    val entryPrice: Double?,
    val targetPrice: Double?,
    val status: String,
    val exitDate: String?,
    val exitPrice: Double?,
    val holdingTradingDays: Int?,
    val maximumHighSinceEntryPct: Double?,
    val unresolvedCloseReturnPct: Double?,
)

data class VolumeEventConfirmationBacktestSummary(
    val setupCount: Int,
    val confirmedSignalCount: Int,
    val targetHitCount: Int,
    val unresolvedCount: Int,
    val noConfirmationCount: Int,
    val skippedWhileInPositionCount: Int,
    val insufficientForwardDataCount: Int,
    val rejectedBearishContextCount: Int,
    val insufficientRsiCalibrationCount: Int,
    val rsiAboveAdaptiveCeilingCount: Int,
    val pastRsiTrendRejectedCount: Int,
    val confirmationRatePct: Double?,
    val targetHitRatePct: Double?,
    val averageHoldingTradingDays: Double?,
)

data class VolumeEventConfirmationSymbolReport(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
    val dataStatus: String,
    val testedFromDate: String?,
    val testedToDate: String?,
    val summary: VolumeEventConfirmationBacktestSummary,
    val observations: List<VolumeEventConfirmationObservation>,
)

data class VolumeEventConfirmationBacktestReport(
    val watchlistKey: String,
    val selectedSymbol: String?,
    val testedFromDate: String?,
    val testedToDate: String?,
    val config: VolumeEventConfirmationBacktestConfig,
    val summary: VolumeEventConfirmationBacktestSummary,
    val symbols: List<VolumeEventConfirmationSymbolReport>,
)

object VolumeEventEntryModes {
    const val FIVE_DAY_FUTURE_RSI_CONFIRMATION = "FIVE_DAY_FUTURE_RSI_CONFIRMATION"
    const val FIVE_DAY_PAST_RSI_EARLY_ENTRY = "FIVE_DAY_PAST_RSI_EARLY_ENTRY"

    val all: Set<String> = setOf(
        FIVE_DAY_FUTURE_RSI_CONFIRMATION,
        FIVE_DAY_PAST_RSI_EARLY_ENTRY,
    )
}

internal data class VolumeEventConfirmationMember(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
)

data class AdaptiveRsiCalibration(
    val threshold: Double?,
    val sampleCount: Int,
    val baselineHitRatePct: Double?,
    val selectedHitRatePct: Double?,
)

interface AdaptiveRsiCalibrationProvider {
    fun calculate(
        candles: List<DailyCandle>,
        rsiValues: List<Double>,
        currentEventIndex: Int,
        config: VolumeEventConfirmationBacktestConfig,
    ): AdaptiveRsiCalibration
}

object VolumeEventConfirmationStatuses {
    const val TARGET_HIT = "TARGET_HIT"
    const val UNRESOLVED = "UNRESOLVED"
    const val NO_CONFIRMATION = "NO_CONFIRMATION"
    const val SKIPPED_WHILE_IN_POSITION = "SKIPPED_WHILE_IN_POSITION"
    const val INSUFFICIENT_FORWARD_DATA = "INSUFFICIENT_FORWARD_DATA"
    const val REJECTED_BEARISH_CONTEXT = "REJECTED_BEARISH_CONTEXT"
    const val INSUFFICIENT_RSI_CALIBRATION = "INSUFFICIENT_RSI_CALIBRATION"
    const val RSI_ABOVE_ADAPTIVE_CEILING = "RSI_ABOVE_ADAPTIVE_CEILING"
    const val PAST_RSI_TREND_NOT_CONFIRMED = "PAST_RSI_TREND_NOT_CONFIRMED"
}

object VolumeEventConfirmationDataStatuses {
    const val AVAILABLE = "AVAILABLE"
    const val INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY"
    const val NO_CANDLES = "NO_CANDLES"
}
