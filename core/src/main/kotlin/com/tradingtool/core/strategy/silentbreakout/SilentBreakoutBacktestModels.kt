package com.tradingtool.core.strategy.silentbreakout

data class SilentBreakoutBacktestRequest(
    val csvContent: String,
    val targetPct: Double,
)

enum class SilentBreakoutDataStatus {
    AVAILABLE,
    MISSING_SIGNAL_CANDLE,
    PARTIAL_HISTORY,
}

data class SilentBreakoutBacktestRow(
    val symbol: String,
    val instrumentToken: Long?,
    val signalDate: String,
    val dataStatus: SilentBreakoutDataStatus,
    val signalClose: Double?,
    val distanceFromFiftyTwoWeekHighPct: Double?,
    val roc20Pct: Double?,
    val distanceFromSma200Pct: Double?,
    val lateStageRisk: Boolean?,
    val priorFiveSessionsMaxDeliveryPct: Double?,
    val entryDate: String?,
    val entryPrice: Double?,
    val targetPrice: Double?,
    val targetAchieved: Boolean?,
    val targetAchievedDays: Int?,
    val nextFiveSessionsLow: Double?,
    val nextFiveSessionsLowMovePct: Double?,
    val nextFiveSessionsLowDays: Int?,
    val forward20SessionReturnPct: Double?,
    val forward40SessionReturnPct: Double?,
    val maxGain40SessionsPct: Double?,
    val maxDrawdown40SessionsPct: Double?,
)

data class SilentBreakoutBacktestSummary(
    val signalCount: Int,
    val availableCount: Int,
    val lateStageRiskCount: Int,
    val averageForward20SessionReturnPct: Double?,
    val averageForward40SessionReturnPct: Double?,
)

data class SilentBreakoutBacktestResponse(
    val rows: List<SilentBreakoutBacktestRow>,
    val summary: SilentBreakoutBacktestSummary,
)
