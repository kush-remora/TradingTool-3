package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import kotlin.math.round

internal object BreakoutDayQualityAnalyzer {
    fun analyze(
        symbol: String,
        candles: List<DailyCandle>,
        deliveries: List<StockDeliveryDaily>,
        evaluation: AdaptiveBreakoutEvaluation,
    ): BreakoutDayQualityResponse {
        val orderedCandles = candles.distinctBy(DailyCandle::candleDate).sortedBy(DailyCandle::candleDate)
        val latest = orderedCandles.last()
        val step = evaluation.rawSteps.last()
        val breakoutLine = step.breakoutBoundary ?: step.ceilingUpperBoundary
        val priorCandles = orderedCandles.dropLast(1)
        val priorVolumeAverage = priorCandles.takeLast(VOLUME_BASELINE_SESSIONS)
            .takeIf { baseline -> baseline.size == VOLUME_BASELINE_SESSIONS }
            ?.map { candle -> candle.volume.toDouble() }
            ?.average()
            ?.takeIf { average -> average > 0.0 }
        val volumeRatio = priorVolumeAverage?.let { average -> latest.volume / average }

        val selectedDelivery = deliveries.firstOrNull { delivery -> delivery.tradingDate == latest.candleDate }
        val priorDeliveredQuantities = deliveries
            .filter { delivery -> delivery.tradingDate < latest.candleDate }
            .sortedBy(StockDeliveryDaily::tradingDate)
            .mapNotNull(StockDeliveryDaily::delivQty)
            .takeLast(DELIVERY_BASELINE_SESSIONS)
        val deliveryAverage = priorDeliveredQuantities
            .takeIf { quantities -> quantities.size == DELIVERY_BASELINE_SESSIONS }
            ?.map(Long::toDouble)
            ?.average()
            ?.takeIf { average -> average > 0.0 }
        val deliveryRatio = selectedDelivery?.delivQty?.let { quantity ->
            deliveryAverage?.let { average -> quantity / average }
        }
        val closePosition = if (latest.high > latest.low) {
            (latest.close - latest.low) / (latest.high - latest.low)
        } else {
            null
        }
        val lineClearanceAtr = evaluation.latestAtr.takeIf { atr -> atr > 0.0 }?.let { atr ->
            breakoutLine?.let { line -> (latest.close - line) / atr }
        }
        val chartContext = BreakoutChartContextAnalyzer.analyze(
            candles = orderedCandles,
            close = latest.close,
            atr = evaluation.latestAtr,
            majorCeiling = step.majorCeilingUpperBoundary,
        )

        val rules = listOf(
            closePositionRule(closePosition),
            volumeRule(volumeRatio),
            deliveryRule(deliveryRatio),
            breakoutLineRule(lineClearanceAtr, breakoutLine),
            extensionRule(lineClearanceAtr, breakoutLine),
        )
        val isFreshBreakout = step.decision == AdaptiveBreakoutDecision.FRESH_BREAKOUT
        val overallDecision = overallDecision(isFreshBreakout, rules)

        return BreakoutDayQualityResponse(
            symbol = symbol,
            date = latest.candleDate.toString(),
            structureStatus = evaluation.status,
            structureDecision = step.decision,
            structureExplanation = step.explanation,
            overallDecision = overallDecision,
            decisionSummary = decisionSummary(overallDecision, step.decision),
            open = latest.open,
            high = latest.high,
            low = latest.low,
            close = latest.close,
            volume = latest.volume,
            atr = evaluation.latestAtr,
            floor = step.candidateFloor,
            peak = step.candidatePeak,
            breakoutLine = breakoutLine,
            majorCeiling = step.majorCeilingUpperBoundary,
            sma50 = chartContext.sma50,
            sma200 = chartContext.sma200,
            deliveryPercentage = selectedDelivery?.delivPer,
            deliveredQuantity = selectedDelivery?.delivQty,
            rules = rules,
            chartContext = chartContext,
        )
    }

    private fun closePositionRule(position: Double?): BreakoutQualityRuleResult {
        if (position == null) {
            return unavailable(
                "close-position",
                "Close position",
                "Daily range is zero, usually because the stock was circuit-locked; next-open execution is uncertain.",
            )
        }
        val verdict = when {
            position >= 0.80 -> BreakoutQualityVerdict.PASS
            position >= 0.60 -> BreakoutQualityVerdict.WAIT
            else -> BreakoutQualityVerdict.REJECT
        }
        return rule(
            key = "close-position",
            label = "Close near the high",
            rule = "Pass ≥80% · Wait 60–79% · Reject <60%",
            actual = "${roundTo1(position * 100)}% of the daily range",
            verdict = verdict,
            explanation = when (verdict) {
                BreakoutQualityVerdict.PASS -> "Buyers held control into the close."
                BreakoutQualityVerdict.WAIT -> "The finish was acceptable, but not decisive."
                else -> "Price gave back too much of the day's move."
            },
        )
    }

    private fun volumeRule(ratio: Double?): BreakoutQualityRuleResult {
        if (ratio == null) return unavailable("volume", "Volume participation", "Ten prior sessions are required.")
        val verdict = when {
            ratio >= 1.5 -> BreakoutQualityVerdict.PASS
            ratio >= 1.0 -> BreakoutQualityVerdict.WAIT
            else -> BreakoutQualityVerdict.REJECT
        }
        return rule(
            key = "volume",
            label = "Volume vs prior 10D",
            rule = "Pass ≥1.5× · Wait 1.0–1.49× · Reject <1.0×",
            actual = "${roundTo2(ratio)}×",
            verdict = verdict,
            explanation = when (verdict) {
                BreakoutQualityVerdict.PASS -> "The move had clearly above-normal trading activity."
                BreakoutQualityVerdict.WAIT -> "Participation was normal, not exceptional."
                else -> "The breakout attempt lacked volume support."
            },
        )
    }

    private fun deliveryRule(ratio: Double?): BreakoutQualityRuleResult {
        if (ratio == null) return unavailable("delivery", "Delivery participation", "Selected-day delivery and 20 prior delivery sessions are required.")
        val verdict = when {
            ratio >= 1.25 -> BreakoutQualityVerdict.PASS
            ratio >= 1.0 -> BreakoutQualityVerdict.WAIT
            else -> BreakoutQualityVerdict.REJECT
        }
        return rule(
            key = "delivery",
            label = "Delivered quantity vs prior 20D",
            rule = "Pass ≥1.25× · Wait 1.0–1.24× · Reject <1.0×",
            actual = "${roundTo2(ratio)}×",
            verdict = verdict,
            explanation = when (verdict) {
                BreakoutQualityVerdict.PASS -> "Delivery participation expanded with the move."
                BreakoutQualityVerdict.WAIT -> "Delivery was near its normal level."
                else -> "Delivered quantity did not confirm the move."
            },
        )
    }

    private fun breakoutLineRule(clearanceAtr: Double?, breakoutLine: Double?): BreakoutQualityRuleResult {
        if (clearanceAtr == null || breakoutLine == null) {
            return unavailable("breakout-line", "Close above breakout line", "No active ceiling was available on this date.")
        }
        val verdict = when {
            clearanceAtr >= CLEAR_CLOSE_ATR -> BreakoutQualityVerdict.PASS
            clearanceAtr > 0.0 -> BreakoutQualityVerdict.WAIT
            else -> BreakoutQualityVerdict.REJECT
        }
        return rule(
            key = "breakout-line",
            label = "Close above breakout line",
            rule = "Pass ≥0.10 ATR above · Wait 0–0.09 ATR above · Reject at/below",
            actual = "${signed(roundTo2(clearanceAtr))} ATR vs ₹${roundTo2(breakoutLine)}",
            verdict = verdict,
            explanation = when (verdict) {
                BreakoutQualityVerdict.PASS -> "The close cleared the line with useful separation."
                BreakoutQualityVerdict.WAIT -> "The close cleared the line, but only narrowly."
                else -> "The close did not hold above the breakout line."
            },
        )
    }

    private fun extensionRule(clearanceAtr: Double?, breakoutLine: Double?): BreakoutQualityRuleResult {
        if (clearanceAtr == null || breakoutLine == null) {
            return unavailable("extension", "Price extension", "No active ceiling was available on this date.")
        }
        val verdict = when {
            clearanceAtr <= 0.0 -> BreakoutQualityVerdict.REJECT
            clearanceAtr <= 0.5 -> BreakoutQualityVerdict.PASS
            clearanceAtr <= 1.0 -> BreakoutQualityVerdict.WAIT
            else -> BreakoutQualityVerdict.REJECT
        }
        return rule(
            key = "extension",
            label = "Close not too extended",
            rule = "Pass 0–0.50 ATR · Wait 0.51–1.0 ATR · Reject >1.0 ATR",
            actual = "${signed(roundTo2(clearanceAtr))} ATR from ₹${roundTo2(breakoutLine)}",
            verdict = verdict,
            explanation = when {
                clearanceAtr <= 0.0 -> "Price did not finish above the line."
                verdict == BreakoutQualityVerdict.PASS -> "The close remained near enough to the breakout line."
                verdict == BreakoutQualityVerdict.WAIT -> "The move may need a calmer entry or pullback."
                else -> "The close was already stretched too far from the line."
            },
        )
    }

    private fun overallDecision(
        isFreshBreakout: Boolean,
        rules: List<BreakoutQualityRuleResult>,
    ): BreakoutQualityDecision {
        if (!isFreshBreakout) return BreakoutQualityDecision.CONTEXT_ONLY
        if (rules.any { result -> result.verdict == BreakoutQualityVerdict.REJECT }) return BreakoutQualityDecision.REJECT
        if (rules.any { result -> result.verdict != BreakoutQualityVerdict.PASS }) return BreakoutQualityDecision.WAIT
        return BreakoutQualityDecision.PASS
    }

    private fun decisionSummary(
        decision: BreakoutQualityDecision,
        structureDecision: AdaptiveBreakoutDecision,
    ): String = when (decision) {
        BreakoutQualityDecision.PASS -> "Fresh breakout with all five quality checks passed."
        BreakoutQualityDecision.WAIT -> "Fresh breakout, but confirmation is mixed or incomplete. Wait for a safer entry."
        BreakoutQualityDecision.REJECT -> "Fresh breakout signal, but at least one quality check failed. Do not chase it."
        BreakoutQualityDecision.CONTEXT_ONLY -> if (structureDecision == AdaptiveBreakoutDecision.EARLY_BREAKOUT) {
            "Early breakout is a watch-only signal from an unconfirmed compact ceiling. Use the checks as context; it is not a fresh-breakout entry day."
        } else {
            "This was ${structureDecision.name.lowercase().replace('_', ' ')}, not a fresh-breakout entry day. Use the checks as context only."
        }
    }

    private fun unavailable(key: String, label: String, explanation: String): BreakoutQualityRuleResult = rule(
        key = key,
        label = label,
        rule = when (key) {
            "close-position" -> "Pass ≥80% · Wait 60–79% · Reject <60%"
            "volume" -> "Pass ≥1.5× · Wait 1.0–1.49× · Reject <1.0×"
            "delivery" -> "Pass ≥1.25× · Wait 1.0–1.24× · Reject <1.0×"
            "breakout-line" -> "Pass ≥0.10 ATR above · Wait 0–0.09 ATR above · Reject at/below"
            else -> "Pass 0–0.50 ATR · Wait 0.51–1.0 ATR · Reject >1.0 ATR"
        },
        actual = "Unavailable",
        verdict = BreakoutQualityVerdict.UNAVAILABLE,
        explanation = explanation,
    )

    private fun rule(
        key: String,
        label: String,
        rule: String,
        actual: String,
        verdict: BreakoutQualityVerdict,
        explanation: String,
    ): BreakoutQualityRuleResult = BreakoutQualityRuleResult(key, label, rule, actual, verdict, explanation)

    private fun signed(value: Double): String = if (value > 0.0) "+$value" else value.toString()

    private fun roundTo1(value: Double): Double = round(value * 10.0) / 10.0

    private fun roundTo2(value: Double): Double = round(value * 100.0) / 100.0

    private const val VOLUME_BASELINE_SESSIONS = 10
    private const val DELIVERY_BASELINE_SESSIONS = 20
    private const val CLEAR_CLOSE_ATR = 0.10
}
