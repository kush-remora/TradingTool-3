package com.tradingtool.core.strategy.csvbacktest

data class CsvBacktestApiRequest(
    val csvContent: String,
    val type: String,
    val targetPct: Double?,
    val stopLossPct: Double,
    val initialStopLossSessions: Int = 5,
    val trailingStopLossPct: Double = 5.0,
    val entryStrategy: String = "NEXT_DAY_OPEN",
    val retestWindowDays: Int = 5,
    val retestTolerancePct: Double = 1.0,
    val applyV2Validation: Boolean = false,
    val breakoutLookbackSessions: Int = 100,
    val maxCloseToCloseGainPct: Double = 6.0,
)

data class CsvBacktestTradeResult(
    val symbol: String,
    val instrumentToken: Long?,
    val marketCapName: String,
    val sector: String,
    val signalDate: String,
    val entryStrategy: String,
    val breakoutLevel: Double?,
    val breakoutSpanSessions: Int?,
    val breakoutSpanIsLowerBound: Boolean,
    val breakoutDayMovePct: Double?,
    val breakoutDayDeliveryPct: Double?,
    val priorFiveDaysMaxDeliveryPct: Double?,
    val priorFiveDaysDelivery: List<CsvBacktestPriorDeliveryDay>,
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

data class CsvBacktestPriorDeliveryDay(
    val date: String,
    val deliveryPct: Double?,
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
