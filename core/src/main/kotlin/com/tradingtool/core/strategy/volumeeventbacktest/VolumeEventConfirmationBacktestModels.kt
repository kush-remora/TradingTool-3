package com.tradingtool.core.strategy.volumeeventbacktest

data class VolumeEventConfirmationBacktestRequest(
    val watchlistKey: String? = null,
    val symbol: String? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
)

data class VolumeEventConfirmationBacktestConfig(
    val volumeBaselineDays: Int = 5,
    val volumeShockMultiplier: Double = 2.0,
    val lowRsiThreshold: Double = 35.0,
    val confirmationDays: Int = 5,
    val targetPct: Double = 5.0,
    val maxHoldingDays: Int = 15,
    val rsiPeriod: Int = 14,
)

data class VolumeEventConfirmationObservation(
    val symbol: String,
    val eventDate: String,
    val eventClose: Double,
    val eventVolume: Long,
    val priorVolumeAverage: Double,
    val volumeRatio: Double,
    val eventRsi: Double,
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

internal data class VolumeEventConfirmationMember(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
)

object VolumeEventConfirmationStatuses {
    const val TARGET_HIT = "TARGET_HIT"
    const val UNRESOLVED = "UNRESOLVED"
    const val NO_CONFIRMATION = "NO_CONFIRMATION"
    const val SKIPPED_WHILE_IN_POSITION = "SKIPPED_WHILE_IN_POSITION"
    const val INSUFFICIENT_FORWARD_DATA = "INSUFFICIENT_FORWARD_DATA"
}

object VolumeEventConfirmationDataStatuses {
    const val AVAILABLE = "AVAILABLE"
    const val INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY"
    const val NO_CANDLES = "NO_CANDLES"
}
