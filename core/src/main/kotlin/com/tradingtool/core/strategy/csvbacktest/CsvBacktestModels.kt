package com.tradingtool.core.strategy.csvbacktest

data class CsvBacktestApiRequest(
    val csvContent: String,
    val type: String,
    val targetPct: Double?,
    val stopLossPct: Double,
    val entryStrategy: String = "NEXT_DAY_OPEN",
    val retestWindowDays: Int = 5,
    val retestTolerancePct: Double = 1.0,
    val applyV2Validation: Boolean = false,
)

data class CsvBacktestTradeResult(
    val symbol: String,
    val instrumentToken: Long?,
    val marketCapName: String,
    val sector: String,
    val signalDate: String,
    val entryStrategy: String,
    val breakoutLevel: Double?,
    val entryDate: String?,
    val entryPrice: Double?,
    val firstFiveDaysLowestPrice: Double?,
    val firstFiveDaysDropAmount: Double?,
    val firstFiveDaysDropPct: Double?,
    val firstThreeDaysRedCandleCount: Int?,
    val v2MaxPreBreakoutVolumeRatio: Double?,
    val v2FailedResistanceAttempts: Int?,
    val v2RecentRunBasePrice: Double?,
    val v2MoveFromRecentBasePct: Double?,
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
    val avgProfitPct: Double,
    val avgFirstFiveDaysDropPct: Double,
)

data class CsvBacktestResponse(
    val trades: List<CsvBacktestTradeResult>,
    val summaries: List<CsvBacktestSummary>,
    val inputSignalCount: Int,
    val validatedSignalCount: Int,
)
