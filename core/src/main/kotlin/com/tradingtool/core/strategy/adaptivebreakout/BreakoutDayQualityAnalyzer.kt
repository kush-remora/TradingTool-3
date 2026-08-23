package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.technical.roundTo2
import kotlin.math.round

/** Calculates the two quality measures currently under experiment. Delivery remains context only. */
internal object BreakoutDayQualityAnalyzer {

    fun analyze(
        symbol: String,
        candles: List<DailyCandle>,
        deliveries: List<StockDeliveryDaily>,
        evaluation: AdaptiveBreakoutEvaluation,
    ): BreakoutDayQualityResponse = analyzeStep(
        symbol = symbol,
        candles = candles,
        deliveries = deliveries,
        step = evaluation.rawSteps.last(),
        structureStatus = evaluation.status,
        latestAtr = evaluation.latestAtr,
    )

    internal fun analyzeStep(
        symbol: String,
        candles: List<DailyCandle>,
        deliveries: List<StockDeliveryDaily>,
        step: AdaptiveBreakoutRawStep,
    ): BreakoutDayQualityResponse = analyzeStep(
        symbol = symbol,
        candles = candles,
        deliveries = deliveries,
        step = step,
        structureStatus = step.decision.toStatus(),
        latestAtr = step.atr,
    )

    private fun analyzeStep(
        symbol: String,
        candles: List<DailyCandle>,
        deliveries: List<StockDeliveryDaily>,
        step: AdaptiveBreakoutRawStep,
        structureStatus: AdaptiveBreakoutStatus,
        latestAtr: Double,
    ): BreakoutDayQualityResponse {
        val orderedCandles = candles.distinctBy(DailyCandle::candleDate).sortedBy(DailyCandle::candleDate)
        require(orderedCandles.isNotEmpty()) { "At least one candle is required." }
        val latest = orderedCandles.last()
        val priorCandles = orderedCandles.dropLast(1)
        val chartContext = BreakoutChartContextAnalyzer.analyze(
            candles = orderedCandles,
            close = latest.close,
            atr = latestAtr,
            majorCeiling = step.majorCeilingUpperBoundary,
        )
        val rules = listOf(
            baseQualityCheck(priorCandles),
            breakoutDayMoveCheck(priorCandles, latest),
        )
        val volumeFootprint = volumeFootprintCheck(priorCandles, latest)
        val isFreshBreakout = step.decision == AdaptiveBreakoutDecision.FRESH_BREAKOUT
        val overallDecision = overallDecision(isFreshBreakout, rules)

        return BreakoutDayQualityResponse(
            symbol = symbol,
            date = latest.candleDate.toString(),
            structureStatus = structureStatus,
            structureDecision = step.decision,
            structureExplanation = step.explanation,
            overallDecision = overallDecision,
            decisionSummary = decisionSummary(overallDecision, step.decision, rules),
            open = latest.open,
            high = latest.high,
            low = latest.low,
            close = latest.close,
            volume = latest.volume,
            twentyDayQuietAverageVolume = quietVolumeAverage(priorCandles),
            volumeVsTenDayAveragePct = volumeVsTenDayAveragePct(priorCandles, latest),
            volumeVsTwentyDayQuietAveragePct = volumeVsTwentyDayQuietAveragePct(priorCandles, latest),
            volumeVsPriorTwentyDayHighChangePct = volumeVsPriorTwentyDayHighChangePct(priorCandles, latest),
            recentAbnormalVolumeDays = recentAbnormalVolumeDays(priorCandles),
            atr = latestAtr,
            floor = step.candidateFloor,
            peak = step.candidatePeak,
            breakoutLine = step.breakoutBoundary ?: step.ceilingUpperBoundary,
            majorCeiling = step.majorCeilingUpperBoundary,
            sma50 = chartContext.sma50,
            sma200 = chartContext.sma200,
            deliveryContext = buildDeliveryContext(latest, deliveries),
            volumeFootprint = volumeFootprint,
            rules = rules,
            chartContext = chartContext,
        )
    }

    private fun volumeVsTenDayAveragePct(
        priorCandles: List<DailyCandle>,
        latest: DailyCandle,
    ): Double? {
        val average = priorCandles
            .takeLast(VOLUME_BASELINE_SESSIONS)
            .takeIf { candles -> candles.size == VOLUME_BASELINE_SESSIONS }
            ?.map(DailyCandle::volume)
            ?.average()
            ?: return null
        return if (average > 0.0) (latest.volume / average * 100.0).roundTo2() else null
    }

    private fun volumeVsTwentyDayQuietAveragePct(
        priorCandles: List<DailyCandle>,
        latest: DailyCandle,
    ): Double? {
        val quietAverage = quietVolumeAverage(priorCandles)
            ?: return null
        return if (quietAverage > 0.0) {
            (latest.volume / quietAverage * 100.0).roundTo2()
        } else {
            null
        }
    }

    private fun quietVolumeAverage(priorCandles: List<DailyCandle>): Double? = priorCandles
        .takeLast(VOLUME_QUIET_LOOKBACK_SESSIONS)
        .takeIf { candles -> candles.size == VOLUME_QUIET_LOOKBACK_SESSIONS }
        ?.map(DailyCandle::volume)
        ?.sorted()
        ?.take(VOLUME_QUIET_BASELINE_SESSIONS)
        ?.average()
        ?.takeIf { average -> average > 0.0 }

    private fun recentAbnormalVolumeDays(priorCandles: List<DailyCandle>): List<BreakoutRecentVolumeDay> {
        val quietAverage = quietVolumeAverage(priorCandles) ?: return emptyList()
        return priorCandles
            .takeLast(VOLUME_QUIET_LOOKBACK_SESSIONS)
            .mapNotNull { candle ->
                val volumeVsQuietAveragePct = (candle.volume / quietAverage * 100.0).roundTo2()
                if (volumeVsQuietAveragePct < ABNORMAL_VOLUME_MULTIPLE * 100.0) return@mapNotNull null
                BreakoutRecentVolumeDay(
                    date = candle.candleDate.toString(),
                    open = candle.open,
                    high = candle.high,
                    low = candle.low,
                    close = candle.close,
                    volume = candle.volume,
                    volumeVsQuietAveragePct = volumeVsQuietAveragePct,
                    lowToHighPct = movementPct(candle.low, candle.high),
                    openToClosePct = movementPct(candle.open, candle.close),
                )
            }
            .asReversed()
    }

    private fun movementPct(from: Double, to: Double): Double? {
        if (from <= 0.0) return null
        return ((to / from - 1.0) * 100.0).roundTo2()
    }

    private fun volumeVsPriorTwentyDayHighChangePct(
        priorCandles: List<DailyCandle>,
        latest: DailyCandle,
    ): Double? {
        val priorHigh = priorCandles
            .takeLast(VOLUME_QUIET_LOOKBACK_SESSIONS)
            .takeIf { candles -> candles.size == VOLUME_QUIET_LOOKBACK_SESSIONS }
            ?.maxOfOrNull(DailyCandle::volume)
            ?: return null
        return if (priorHigh > 0L) {
            (((latest.volume.toDouble() / priorHigh) - 1.0) * 100.0).roundTo2()
        } else {
            null
        }
    }

    private fun AdaptiveBreakoutDecision.toStatus(): AdaptiveBreakoutStatus = when (this) {
        AdaptiveBreakoutDecision.FRESH_BREAKOUT -> AdaptiveBreakoutStatus.FRESH_BREAKOUT
        AdaptiveBreakoutDecision.EARLY_BREAKOUT -> AdaptiveBreakoutStatus.EARLY_BREAKOUT
        AdaptiveBreakoutDecision.BREAKOUT_CONTINUATION -> AdaptiveBreakoutStatus.BREAKOUT_CONTINUATION
        AdaptiveBreakoutDecision.STRONG_REBOUND -> AdaptiveBreakoutStatus.STRONG_REBOUND
        AdaptiveBreakoutDecision.BUILDING_STRUCTURE -> AdaptiveBreakoutStatus.NO_CEILING
        else -> AdaptiveBreakoutStatus.BELOW_CEILING
    }

    private fun baseQualityCheck(priorCandles: List<DailyCandle>): BreakoutQualityRuleResult {
        val history = priorCandles.takeLast(MAX_BASE_LOOKBACK_SESSIONS)
        if (history.isEmpty()) return unavailable("base-quality", "Base quality", "Pre-breakout history is unavailable.")
        val passBase = longestBase(history, PASS_BASE_WIDTH_PCT)
        val waitBase = longestBase(history, WAIT_BASE_WIDTH_PCT)
        val selectedBase = if (passBase.duration >= PASS_BASE_SESSIONS) passBase else waitBase
        val duration = selectedBase.duration
        val widthPct = selectedBase.widthPct
        val verdict = when {
            passBase.duration >= PASS_BASE_SESSIONS -> BreakoutQualityVerdict.PASS
            waitBase.duration >= WAIT_BASE_SESSIONS -> BreakoutQualityVerdict.WAIT
            else -> BreakoutQualityVerdict.REJECT
        }
        return rule(
            key = "base-quality",
            label = "Base quality",
            rule = "Pass ≥$PASS_BASE_SESSIONS sessions and ≤${PASS_BASE_WIDTH_PCT}% width · Wait ≥$WAIT_BASE_SESSIONS sessions and ≤${WAIT_BASE_WIDTH_PCT}% width",
            actual = "$duration sessions · ${roundTo1(widthPct)}% high-low width · " +
                "${selectedBase.startDate} to ${selectedBase.endDate}",
            verdict = verdict,
            explanation = when (verdict) {
                BreakoutQualityVerdict.PASS -> "Price built a long, comparatively tight base before the breakout."
                BreakoutQualityVerdict.WAIT -> "A base exists, but its duration or width is only moderate."
                BreakoutQualityVerdict.REJECT -> "The breakout lacks a sufficiently long, tight congestion base."
                BreakoutQualityVerdict.UNAVAILABLE -> "Base history is unavailable."
            },
        )
    }

    private fun longestBase(
        history: List<DailyCandle>,
        maximumWidthPct: Double,
    ): BaseWindow {
        val candidates = history.indices.mapNotNull { startIndex ->
            val candles = history.subList(startIndex, history.size)
            val lowestLow = candles.minOf(DailyCandle::low)
            if (lowestLow <= 0.0) return@mapNotNull null
            val widthPct = (candles.maxOf(DailyCandle::high) / lowestLow - 1.0) * 100.0
            if (widthPct > maximumWidthPct) return@mapNotNull null
            BaseWindow(
                duration = candles.size,
                widthPct = widthPct,
                startDate = candles.first().candleDate.toString(),
                endDate = candles.last().candleDate.toString(),
            )
        }
        return candidates.maxWithOrNull(compareBy<BaseWindow> { it.duration }.thenByDescending { -it.widthPct })
            ?: BaseWindow(0, Double.POSITIVE_INFINITY, "unavailable", "unavailable")
    }

    private fun breakoutDayMoveCheck(
        priorCandles: List<DailyCandle>,
        latest: DailyCandle,
    ): BreakoutQualityRuleResult {
        val previousClose = priorCandles.lastOrNull()?.close
        if (previousClose == null || previousClose <= 0.0) return unavailable("breakout-day-move", "Breakout-day move", "Previous close is unavailable.")
        val movePct = (latest.close / previousClose - 1.0) * 100.0
        val verdict = when {
            movePct < PASS_MOVE_MAX_PCT -> BreakoutQualityVerdict.PASS
            movePct <= WAIT_MOVE_MAX_PCT -> BreakoutQualityVerdict.WAIT
            else -> BreakoutQualityVerdict.REJECT
        }
        return rule(
            key = "breakout-day-move",
            label = "Breakout-day move",
            rule = "Pass <${PASS_MOVE_MAX_PCT}% close-to-close · Wait ${PASS_MOVE_MAX_PCT}%–${WAIT_MOVE_MAX_PCT}% · Reject >${WAIT_MOVE_MAX_PCT}%",
            actual = "${roundTo1(movePct)}% from previous close",
            verdict = verdict,
            explanation = when (verdict) {
                BreakoutQualityVerdict.PASS -> "The breakout is not excessively extended on its signal day."
                BreakoutQualityVerdict.WAIT -> "The signal day is meaningfully extended; continuation needs confirmation."
                BreakoutQualityVerdict.REJECT -> "The signal day is highly extended and has poor entry asymmetry."
                BreakoutQualityVerdict.UNAVAILABLE -> "Breakout-day move is unavailable."
            },
        )
    }

    private fun volumeFootprintCheck(
        priorCandles: List<DailyCandle>,
        breakoutCandle: DailyCandle,
    ): BreakoutVolumeFootprintResult {
        if (priorCandles.size < VOLUME_LOOKBACK_SESSIONS) {
            return unavailableVolumeFootprint("Need at least $VOLUME_LOOKBACK_SESSIONS sessions before breakout.")
        }
        val priceWindow = priorCandles.takeLast(VOLUME_LOOKBACK_SESSIONS)
        val rangeLow = priceWindow.minOf(DailyCandle::low)
        val rangeHigh = priceWindow.maxOf(DailyCandle::high)
        val rangeWidth = rangeHigh - rangeLow
        if (rangeWidth <= 0.0) return unavailableVolumeFootprint("The prior price range is zero.")

        val candidates = priorCandles.indices
            .filter { index -> index >= VOLUME_LOOKBACK_SESSIONS }
            .filter { index -> index >= priorCandles.size - VOLUME_LOOKBACK_SESSIONS }
            .mapNotNull { index ->
                val candle = priorCandles[index]
                val baseline = priorCandles.subList(index - VOLUME_LOOKBACK_SESSIONS, index)
                    .map(DailyCandle::volume)
                    .median()
                if (baseline <= 0.0) return@mapNotNull null
                val volumeRatio = candle.volume / baseline
                val priceRankPct = (candle.close - rangeLow) / rangeWidth * 100.0
                val closePositionPct = closePositionPct(candle)
                if (volumeRatio < FOOTPRINT_VOLUME_MULTIPLE ||
                    priceRankPct > FOOTPRINT_MAX_PRICE_RANK_PCT ||
                    candle.close < candle.open ||
                    closePositionPct < FOOTPRINT_MIN_CLOSE_POSITION_PCT
                ) {
                    return@mapNotNull null
                }
                VolumeFootprintCandidate(index, candle, volumeRatio, priceRankPct)
            }
        val candidate = candidates.maxByOrNull(VolumeFootprintCandidate::volumeRatio)
            ?: return BreakoutVolumeFootprintResult(
                decision = BreakoutVolumeFootprintDecision.REJECT,
                footprintDate = null,
                footprintVolumeRatio = null,
                footprintPriceRankPct = null,
                footprintLowHeld = null,
                distributionWarningDate = null,
                distributionWarningVolumeRatio = null,
                breakoutClosePositionPct = closePositionPct(breakoutCandle),
                explanation = "No qualifying high-volume footprint was found in the prior $VOLUME_LOOKBACK_SESSIONS sessions.",
            )

        val postFootprint = priorCandles.drop(candidate.index + 1)
        val warning = postFootprint.mapIndexedNotNull { offset, candle ->
            val index = candidate.index + 1 + offset
            val baseline = priorCandles.subList(index - VOLUME_LOOKBACK_SESSIONS, index)
                .map(DailyCandle::volume)
                .median()
            val volumeRatio = if (baseline <= 0.0) 0.0 else candle.volume / baseline
            val invalidatesLow = candle.close < candidate.candle.low
            val weakHighVolume = volumeRatio >= DISTRIBUTION_VOLUME_MULTIPLE &&
                candle.close < candle.open &&
                closePositionPct(candle) <= DISTRIBUTION_MAX_CLOSE_POSITION_PCT
            if (invalidatesLow || weakHighVolume) candle to volumeRatio else null
        }.firstOrNull()
        val footprintLowHeld = warning == null || warning.first.close >= candidate.candle.low
        val breakoutClosePositionPct = closePositionPct(breakoutCandle)
        val decision = when {
            warning != null -> BreakoutVolumeFootprintDecision.REJECT
            breakoutClosePositionPct >= PASS_BREAKOUT_CLOSE_POSITION_PCT -> BreakoutVolumeFootprintDecision.PASS
            else -> BreakoutVolumeFootprintDecision.WAIT
        }
        return BreakoutVolumeFootprintResult(
            decision = decision,
            footprintDate = candidate.candle.candleDate.toString(),
            footprintVolumeRatio = candidate.volumeRatio,
            footprintPriceRankPct = candidate.priceRankPct,
            footprintLowHeld = footprintLowHeld,
            distributionWarningDate = warning?.first?.candleDate?.toString(),
            distributionWarningVolumeRatio = warning?.second,
            breakoutClosePositionPct = breakoutClosePositionPct,
            explanation = when (decision) {
                BreakoutVolumeFootprintDecision.PASS -> "A high-volume footprint appeared at a relatively cheap price, held afterward, and no later high-volume rejection was found."
                BreakoutVolumeFootprintDecision.WAIT -> "A qualifying footprint survived without a distribution warning, but the breakout did not close strongly enough."
                BreakoutVolumeFootprintDecision.REJECT -> "The footprint was invalidated or followed by a high-volume weak/rejection candle."
                BreakoutVolumeFootprintDecision.UNAVAILABLE -> "Volume-footprint history is unavailable."
            },
        )
    }

    private fun unavailableVolumeFootprint(explanation: String): BreakoutVolumeFootprintResult =
        BreakoutVolumeFootprintResult(
            decision = BreakoutVolumeFootprintDecision.UNAVAILABLE,
            footprintDate = null,
            footprintVolumeRatio = null,
            footprintPriceRankPct = null,
            footprintLowHeld = null,
            distributionWarningDate = null,
            distributionWarningVolumeRatio = null,
            breakoutClosePositionPct = null,
            explanation = explanation,
        )

    private fun closePositionPct(candle: DailyCandle): Double = if (candle.high <= candle.low) {
        0.0
    } else {
        (candle.close - candle.low) / (candle.high - candle.low) * 100.0
    }

    private data class VolumeFootprintCandidate(
        val index: Int,
        val candle: DailyCandle,
        val volumeRatio: Double,
        val priceRankPct: Double,
    )

    private fun buildDeliveryContext(
        latest: DailyCandle,
        deliveries: List<StockDeliveryDaily>,
    ): BreakoutDeliveryContext? {
        val today = deliveries.firstOrNull { delivery -> delivery.tradingDate == latest.candleDate }
        val baseline = deliveries
            .filter { delivery -> delivery.tradingDate.isBefore(latest.candleDate) }
            .sortedBy(StockDeliveryDaily::tradingDate)
            .takeLast(DELIVERY_BASELINE_SESSIONS)
        if (today == null && baseline.isEmpty()) return null
        val averagePercentage = baseline.mapNotNull(StockDeliveryDaily::delivPer)
            .takeIf(List<Double>::isNotEmpty)?.average()
        val averageQuantity = baseline.mapNotNull(StockDeliveryDaily::delivQty)
            .takeIf(List<Long>::isNotEmpty)?.map(Long::toDouble)?.average()
        val quantityMultiple = averageQuantity
            ?.takeIf { average -> average > 0.0 }
            ?.let { average -> today?.delivQty?.let { quantity -> quantity / average } }
        return BreakoutDeliveryContext(
            deliveryPercentage = today?.delivPer,
            deliveredQuantity = today?.delivQty,
            averageDeliveryPercentage = averagePercentage,
            averageDeliveredQuantity = averageQuantity,
            deliveredQuantityMultiple = quantityMultiple,
        )
    }

    private fun overallDecision(
        isFreshBreakout: Boolean,
        rules: List<BreakoutQualityRuleResult>,
    ): BreakoutQualityDecision {
        if (!isFreshBreakout) return BreakoutQualityDecision.CONTEXT_ONLY
        val available = rules.filter { rule -> rule.verdict != BreakoutQualityVerdict.UNAVAILABLE }
        if (available.isEmpty()) return BreakoutQualityDecision.WAIT
        return when {
            available.any { rule -> rule.verdict == BreakoutQualityVerdict.REJECT } -> BreakoutQualityDecision.REJECT
            available.all { rule -> rule.verdict == BreakoutQualityVerdict.PASS } -> BreakoutQualityDecision.PASS
            else -> BreakoutQualityDecision.WAIT
        }
    }

    private fun decisionSummary(
        decision: BreakoutQualityDecision,
        structureDecision: AdaptiveBreakoutDecision,
        rules: List<BreakoutQualityRuleResult>,
    ): String {
        if (decision == BreakoutQualityDecision.CONTEXT_ONLY) {
            return if (structureDecision == AdaptiveBreakoutDecision.EARLY_BREAKOUT) {
                "Early breakout watch signal from an unconfirmed ceiling. Quality measures are context only."
            } else {
                "${structureDecision.name.lowercase().replace('_', ' ')} — not an entry day. Quality measures are context only."
            }
        }
        val available = rules.filter { rule -> rule.verdict != BreakoutQualityVerdict.UNAVAILABLE }
        val passCount = available.count { rule -> rule.verdict == BreakoutQualityVerdict.PASS }
        return when (decision) {
            BreakoutQualityDecision.PASS -> "$passCount/${available.size} quality measures passed."
            BreakoutQualityDecision.WAIT -> "$passCount/${available.size} quality measures passed; confirmation is mixed."
            BreakoutQualityDecision.REJECT -> "$passCount/${available.size} quality measures passed; at least one measure failed."
            BreakoutQualityDecision.CONTEXT_ONLY -> ""
        }
    }

    private fun unavailable(key: String, label: String, explanation: String): BreakoutQualityRuleResult =
        rule(key, label, "", "Unavailable", BreakoutQualityVerdict.UNAVAILABLE, explanation)

    private fun rule(
        key: String,
        label: String,
        rule: String,
        actual: String,
        verdict: BreakoutQualityVerdict,
        explanation: String,
    ): BreakoutQualityRuleResult = BreakoutQualityRuleResult(key, label, rule, actual, verdict, explanation)

    private fun roundTo1(value: Double): Double = round(value * 10.0) / 10.0

    private data class BaseWindow(
        val duration: Int,
        val widthPct: Double,
        val startDate: String,
        val endDate: String,
    )

    private fun List<Long>.median(): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]).toDouble() / 2.0
        } else {
            sorted[middle].toDouble()
        }
    }

    private const val MAX_BASE_LOOKBACK_SESSIONS = 120
    private const val WAIT_BASE_SESSIONS = 20
    private const val PASS_BASE_SESSIONS = 40
    private const val WAIT_BASE_WIDTH_PCT = 30.0
    private const val PASS_BASE_WIDTH_PCT = 20.0
    private const val PASS_MOVE_MAX_PCT = 3.0
    private const val WAIT_MOVE_MAX_PCT = 6.0
    private const val DELIVERY_BASELINE_SESSIONS = 20
    private const val VOLUME_BASELINE_SESSIONS = 10
    private const val VOLUME_QUIET_LOOKBACK_SESSIONS = 20
    private const val VOLUME_QUIET_BASELINE_SESSIONS = 5
    private const val ABNORMAL_VOLUME_MULTIPLE = 5.0
    private const val VOLUME_LOOKBACK_SESSIONS = 20
    private const val FOOTPRINT_VOLUME_MULTIPLE = 5.0
    private const val FOOTPRINT_MAX_PRICE_RANK_PCT = 60.0
    private const val FOOTPRINT_MIN_CLOSE_POSITION_PCT = 50.0
    private const val DISTRIBUTION_VOLUME_MULTIPLE = 2.0
    private const val DISTRIBUTION_MAX_CLOSE_POSITION_PCT = 35.0
    private const val PASS_BREAKOUT_CLOSE_POSITION_PCT = 60.0
}
