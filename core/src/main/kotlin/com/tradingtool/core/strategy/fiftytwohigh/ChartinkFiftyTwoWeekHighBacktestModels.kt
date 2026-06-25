package com.tradingtool.core.strategy.fiftytwohigh

import com.tradingtool.core.candle.DailyCandle
import java.nio.file.Path
import java.time.LocalDate

data class ChartinkFiftyTwoWeekHighSignal(
    val signalDate: LocalDate,
    val symbol: String,
    val marketCapName: String,
    val sector: String,
)

data class ChartinkFiftyTwoWeekHighBacktestStrategy(
    val name: String,
    val profitTargetPct: Double,
    val stopLossPct: Double,
)

data class ChartinkFiftyTwoWeekHighBacktestConfig(
    val inputFile: Path,
    val strategies: List<ChartinkFiftyTwoWeekHighBacktestStrategy>,
    val priceDataToDate: LocalDate,
)

data class ChartinkFiftyTwoWeekHighSymbolContext(
    val signal: ChartinkFiftyTwoWeekHighSignal,
    val candles: List<DailyCandle>,
)

data class ChartinkFiftyTwoWeekHighTradeRow(
    val strategyName: String,
    val symbol: String,
    val marketCapName: String,
    val marketCapBucket: String,
    val sector: String,
    val signalDate: String,
    val entryDate: String?,
    val exitDate: String?,
    val entryPrice: Double?,
    val exitPrice: Double?,
    val targetPrice: Double?,
    val stopPrice: Double?,
    val outcome: String,
    val success: Boolean,
    val holdingTradingDays: Int?,
    val holdingCalendarDays: Long?,
    val returnPct: Double?,
    val maxFavorableExcursionPct: Double?,
    val maxAdverseExcursionPct: Double?,
    val forward5dReturnPct: Double?,
    val forward10dReturnPct: Double?,
    val forward20dReturnPct: Double?,
    val forward60dReturnPct: Double?,
    val exitWasAmbiguous: Boolean,
    val latestAvailableDate: String?,
)

data class ChartinkFiftyTwoWeekHighBucketSummary(
    val strategyName: String,
    val marketCapBucket: String,
    val totalSignals: Int,
    val enteredTrades: Int,
    val successCount: Int,
    val stopLossCount: Int,
    val endExitCount: Int,
    val noEntryCount: Int,
    val successRatePct: Double,
    val avgHoldingTradingDays: Double?,
    val medianHoldingTradingDays: Double?,
)

data class ChartinkFiftyTwoWeekHighStrategySummary(
    val strategyName: String,
    val totalSignals: Int,
    val enteredTrades: Int,
    val successCount: Int,
    val stopLossCount: Int,
    val endExitCount: Int,
    val noEntryCount: Int,
    val successRatePct: Double,
    val avgHoldingTradingDays: Double?,
    val medianHoldingTradingDays: Double?,
    val buckets: List<ChartinkFiftyTwoWeekHighBucketSummary>,
)

data class ChartinkFiftyTwoWeekHighBacktestReport(
    val generatedAt: String,
    val inputFile: String,
    val priceDataToDate: String,
    val strategies: List<ChartinkFiftyTwoWeekHighBacktestStrategy>,
    val signalCount: Int,
    val uniqueSymbolCount: Int,
    val summaries: List<ChartinkFiftyTwoWeekHighStrategySummary>,
    val trades: List<ChartinkFiftyTwoWeekHighTradeRow>,
)
