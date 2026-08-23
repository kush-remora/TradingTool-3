package com.tradingtool.core.strategy.adaptivebreakout

data class AdaptiveBreakoutBacktestRequest(
    val symbol: String? = null,
    val instrumentToken: Long? = null,
    val watchlistKey: String? = null,
    val months: Long = 6,
    val targetPct: Double = 5.0,
    val stopLossPct: Double = 5.0,
    val entryTrigger: AdaptiveBreakoutBacktestEntryTrigger = AdaptiveBreakoutBacktestEntryTrigger.FRESH_BREAKOUT,
    val qualityFilter: AdaptiveBreakoutQualityFilter = AdaptiveBreakoutQualityFilter.NONE,
    /** Skip entry if the first later tradable open is more than this % above the breakout close. 0.0 = no guard. */
    val maxEntryGapPct: Double = 2.5,
    /** Round-trip transaction costs in percentage points. */
    val transactionCostPct: Double = 0.3,
    /** Round-trip slippage allowance in percentage points. */
    val slippagePct: Double = 0.2,
)

enum class AdaptiveBreakoutBacktestEntryTrigger {
    FRESH_BREAKOUT,
    CEILING_RECLAIM,
}

enum class AdaptiveBreakoutQualityFilter {
    NONE,
    BASE_QUALITY,
}

internal fun AdaptiveBreakoutBacktestEntryTrigger.displayName(): String = when (this) {
    AdaptiveBreakoutBacktestEntryTrigger.FRESH_BREAKOUT -> "Fresh breakout"
    AdaptiveBreakoutBacktestEntryTrigger.CEILING_RECLAIM -> "Ceiling reclaim"
}

internal fun AdaptiveBreakoutQualityFilter.displayName(): String = when (this) {
    AdaptiveBreakoutQualityFilter.NONE -> "no"
    AdaptiveBreakoutQualityFilter.BASE_QUALITY -> "base-quality"
}

internal fun describeEntryGapGuard(maxEntryGapPct: Double): String = if (maxEntryGapPct == 0.0) {
    "No entry-gap guard is applied."
} else {
    "Entries are skipped when the first later tradable open gaps more than ${maxEntryGapPct}% above breakout close."
}

enum class AdaptiveBreakoutBacktestExitReason {
    TARGET_HIT,
    STOP_LOSS,
    STOP_LOSS_SAME_CANDLE,
    END_OF_TEST,
    /** Entry was skipped because the first later tradable open exceeded maxEntryGapPct. */
    SKIPPED_ENTRY_GAP,
    /** Signal was excluded by the selected quality filter. */
    FILTERED_BY_QUALITY,
}

data class AdaptiveBreakoutBacktestTrade(
    val symbol: String,
    val breakoutDate: String,
    val breakoutClose: Double,
    val entryDate: String?,
    val entryPrice: Double?,
    /** Gap between the first later tradable open and breakout close as a percentage. */
    val entryGapPct: Double?,
    val targetPrice: Double?,
    val stopPrice: Double?,
    val exitDate: String?,
    val exitPrice: Double?,
    val exitReason: AdaptiveBreakoutBacktestExitReason,
    val holdingSessions: Int?,
    val returnPct: Double?,
    val ambiguousSameCandle: Boolean,
    val gapThroughExit: Boolean,
    val grossReturnPct: Double?,
    val qualityDecision: BreakoutQualityDecision,
    val qualitySummary: String,
    /** Individual quality check results for analysis. */
    val qualityChecks: List<BreakoutQualityRuleResult> = emptyList(),
    val trendAlignment: BreakoutTrendAlignmentResult? = null,
    val nextSessionBehavior: AdaptiveBreakoutNextSessionBehavior? = null,
    val volumeFootprint: BreakoutVolumeFootprintResult? = null,
    val forwardOutcomes: List<AdaptiveBreakoutForwardOutcome> = emptyList(),
)

data class AdaptiveBreakoutNextSessionBehavior(
    val sessionDate: String,
    val open: Double,
    val low: Double,
    val close: Double,
    val breakoutDayMovePct: Double?,
    val lowVsBreakoutClosePct: Double,
    val lowVsBreakoutLowPct: Double,
    val closeVsBreakoutClosePct: Double,
    val lowVsOpenPct: Double,
)

data class AdaptiveBreakoutForwardOutcome(
    val sessions: Int,
    val closeReturnPct: Double,
    val maxHighReturnPct: Double,
    val targetReached: Boolean,
)

data class AdaptiveBreakoutForwardSummary(
    val sessions: Int,
    val observations: Int,
    val positiveCloseCount: Int,
    val positiveCloseRatePct: Double?,
    val targetReachedCount: Int,
    val targetReachedRatePct: Double?,
    val averageCloseReturnPct: Double?,
)

data class AdaptiveBreakoutBacktestSummary(
    val signalCount: Int,
    val enteredTradeCount: Int,
    val skippedEntryGapCount: Int,
    val qualityFilteredCount: Int,
    val targetHitCount: Int,
    val stopLossCount: Int,
    val endOfTestCount: Int,
    val winRatePct: Double?,
    val averageHoldingSessions: Double?,
    val expectancyPct: Double?,
    val averageWinningReturnPct: Double?,
    val averageLosingReturnPct: Double?,
    val payoffRatio: Double?,
    val profitFactor: Double?,
    val forwardMetrics: List<AdaptiveBreakoutForwardSummary> = emptyList(),
)

data class AdaptiveBreakoutBacktestResponse(
    val symbol: String?,
    val watchlistKey: String? = null,
    val entryTrigger: AdaptiveBreakoutBacktestEntryTrigger,
    val qualityFilter: AdaptiveBreakoutQualityFilter = AdaptiveBreakoutQualityFilter.NONE,
    val testedFromDate: String,
    val testedToDate: String,
    val targetPct: Double,
    val stopLossPct: Double,
    val transactionCostPct: Double,
    val slippagePct: Double,
    val entryRule: String,
    val ambiguousCandleRule: String,
    val summary: AdaptiveBreakoutBacktestSummary,
    val trades: List<AdaptiveBreakoutBacktestTrade>,
    val symbols: List<AdaptiveBreakoutBacktestSymbolReport> = emptyList(),
)

data class AdaptiveBreakoutBacktestSymbolReport(
    val symbol: String,
    val companyName: String?,
    val testedFromDate: String,
    val testedToDate: String,
    val summary: AdaptiveBreakoutBacktestSummary,
    val trades: List<AdaptiveBreakoutBacktestTrade>,
)

internal fun summarizeAdaptiveBreakoutTrades(
    signalCount: Int,
    trades: List<AdaptiveBreakoutBacktestTrade>,
): AdaptiveBreakoutBacktestSummary {
    val targetHitCount = trades.count { trade -> trade.exitReason == AdaptiveBreakoutBacktestExitReason.TARGET_HIT }
    val stopLossCount = trades.count { trade ->
        trade.exitReason == AdaptiveBreakoutBacktestExitReason.STOP_LOSS ||
            trade.exitReason == AdaptiveBreakoutBacktestExitReason.STOP_LOSS_SAME_CANDLE
    }
    val endOfTestCount = trades.count { trade -> trade.exitReason == AdaptiveBreakoutBacktestExitReason.END_OF_TEST }
    val skippedEntryGapCount = trades.count { trade -> trade.exitReason == AdaptiveBreakoutBacktestExitReason.SKIPPED_ENTRY_GAP }
    val qualityFilteredCount = trades.count { trade -> trade.exitReason == AdaptiveBreakoutBacktestExitReason.FILTERED_BY_QUALITY }
    val completedTradeCount = targetHitCount + stopLossCount
    val returns = trades
        .filter { trade -> trade.exitReason != AdaptiveBreakoutBacktestExitReason.FILTERED_BY_QUALITY }
        .mapNotNull { trade -> trade.returnPct }
    val winningReturns = returns.filter { value -> value > 0.0 }
    val losingReturns = returns.filter { value -> value < 0.0 }
    val averageWinningReturn = winningReturns.averageOrNull()
    val averageLosingReturn = losingReturns.averageOrNull()
    val totalWinningReturn = winningReturns.sum()
    val totalLosingReturn = losingReturns.sum()

    return AdaptiveBreakoutBacktestSummary(
        signalCount = signalCount,
        enteredTradeCount = targetHitCount + stopLossCount + endOfTestCount,
        skippedEntryGapCount = skippedEntryGapCount,
        qualityFilteredCount = qualityFilteredCount,
        targetHitCount = targetHitCount,
        stopLossCount = stopLossCount,
        endOfTestCount = endOfTestCount,
        winRatePct = if (completedTradeCount == 0) null else targetHitCount * 100.0 / completedTradeCount,
        averageHoldingSessions = trades.mapNotNull { trade -> trade.holdingSessions }
            .takeIf { sessions -> sessions.isNotEmpty() }
            ?.average(),
        expectancyPct = returns.averageOrNull(),
        averageWinningReturnPct = averageWinningReturn,
        averageLosingReturnPct = averageLosingReturn,
        payoffRatio = if (averageWinningReturn != null && averageLosingReturn != null && averageLosingReturn != 0.0) {
            averageWinningReturn / kotlin.math.abs(averageLosingReturn)
        } else {
            null
        },
        profitFactor = if (totalLosingReturn < 0.0) totalWinningReturn / kotlin.math.abs(totalLosingReturn) else null,
        forwardMetrics = summarizeForwardMetrics(trades),
    )
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

private fun summarizeForwardMetrics(
    trades: List<AdaptiveBreakoutBacktestTrade>,
): List<AdaptiveBreakoutForwardSummary> = FORWARD_HORIZONS.map { horizon ->
    val outcomes = trades
        .filter { trade -> trade.exitReason != AdaptiveBreakoutBacktestExitReason.FILTERED_BY_QUALITY }
        .flatMap { trade -> trade.forwardOutcomes }
        .filter { outcome -> outcome.sessions == horizon }
    val positiveCloseCount = outcomes.count { outcome -> outcome.closeReturnPct > 0.0 }
    val targetReachedCount = outcomes.count(AdaptiveBreakoutForwardOutcome::targetReached)
    AdaptiveBreakoutForwardSummary(
        sessions = horizon,
        observations = outcomes.size,
        positiveCloseCount = positiveCloseCount,
        positiveCloseRatePct = outcomes.percentageOrNull(positiveCloseCount),
        targetReachedCount = targetReachedCount,
        targetReachedRatePct = outcomes.percentageOrNull(targetReachedCount),
        averageCloseReturnPct = outcomes.map(AdaptiveBreakoutForwardOutcome::closeReturnPct).averageOrNull(),
    )
}

private fun List<AdaptiveBreakoutForwardOutcome>.percentageOrNull(count: Int): Double? =
    if (isEmpty()) null else count * 100.0 / size

private val FORWARD_HORIZONS = listOf(5, 10, 20)
