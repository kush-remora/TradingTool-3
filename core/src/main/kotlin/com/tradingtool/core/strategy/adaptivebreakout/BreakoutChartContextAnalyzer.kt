package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.calculateSma
import com.tradingtool.core.technical.getDoubleValue
import com.tradingtool.core.technical.toTa4jSeries
import org.ta4j.core.indicators.SMAIndicator
import kotlin.math.round

internal object BreakoutChartContextAnalyzer {
    fun analyze(
        candles: List<DailyCandle>,
        close: Double,
        atr: Double,
        majorCeiling: Double?,
    ): BreakoutChartContext {
        val movingAverages = movingAverages(candles)
        val priorFiftyTwoWeekHigh = candles.dropLast(1).takeLast(FIFTY_TWO_WEEK_SESSIONS)
            .maxOfOrNull(DailyCandle::high)
            ?.let(::roundTo2)
        val obstacle = listOfNotNull(
            overheadObstacle("Major ceiling", majorCeiling, close),
            overheadObstacle("Prior 52-week high", priorFiftyTwoWeekHigh, close),
            overheadObstacle("200 SMA", movingAverages.sma200, close),
        ).minByOrNull(OverheadObstacle::price)
        val roomPct = obstacle?.let { value -> ((value.price - close) / close) * 100.0 }
        val roomAtr = atr.takeIf { value -> value > 0.0 }?.let { value ->
            obstacle?.let { (it.price - close) / value }
        }
        val rules = contextRules(close, movingAverages, obstacle, priorFiftyTwoWeekHigh, majorCeiling, roomAtr, roomPct)
        val belowFallingSma200 = movingAverages.sma200?.let { close < it } == true &&
            (movingAverages.sma200ChangePctTwentySessions ?: 0.0) < -LONG_TERM_FLAT_TOLERANCE_PCT
        val tooLittleRoom = roomAtr != null && roomAtr < MINIMUM_OBSTACLE_ROOM_ATR
        val decision = when {
            belowFallingSma200 || tooLittleRoom -> BreakoutQualityDecision.REJECT
            rules.any { it.verdict != BreakoutQualityVerdict.PASS } -> BreakoutQualityDecision.WAIT
            else -> BreakoutQualityDecision.PASS
        }
        return BreakoutChartContext(
            overallDecision = decision,
            decisionSummary = decisionSummary(decision, belowFallingSma200, tooLittleRoom, obstacle),
            sma50 = movingAverages.sma50,
            sma200 = movingAverages.sma200,
            sma50ChangePctFiveSessions = movingAverages.sma50ChangePctFiveSessions,
            sma200ChangePctTwentySessions = movingAverages.sma200ChangePctTwentySessions,
            priorFiftyTwoWeekHigh = priorFiftyTwoWeekHigh,
            nextObstaclePrice = obstacle?.price,
            nextObstacleLabel = obstacle?.label,
            roomToObstaclePct = roomPct?.let(::roundTo2),
            roomToObstacleAtr = roomAtr?.let(::roundTo2),
            rules = rules,
        )
    }

    private fun contextRules(
        close: Double,
        movingAverages: MovingAverages,
        obstacle: OverheadObstacle?,
        priorFiftyTwoWeekHigh: Double?,
        majorCeiling: Double?,
        roomAtr: Double?,
        roomPct: Double?,
    ): List<BreakoutQualityRuleResult> {
        val sma50Price = priceVsSmaRule(
            "price-sma50",
            "Price above 50 SMA",
            close,
            movingAverages.sma50,
            false,
            movingAverages.sma200ChangePctTwentySessions,
        )
        val sma50Direction = smaDirectionRule(
            "sma50-direction",
            "50 SMA direction",
            movingAverages.sma50ChangePctFiveSessions,
            SMA50_TREND_SESSIONS,
            SHORT_TERM_FLAT_TOLERANCE_PCT,
            false,
        )
        val sma200Price = priceVsSmaRule(
            "price-sma200",
            "Price above 200 SMA",
            close,
            movingAverages.sma200,
            true,
            movingAverages.sma200ChangePctTwentySessions,
        )
        val sma200Direction = smaDirectionRule(
            "sma200-direction",
            "200 SMA direction",
            movingAverages.sma200ChangePctTwentySessions,
            SMA200_TREND_SESSIONS,
            LONG_TERM_FLAT_TOLERANCE_PCT,
            true,
        )
        return listOf(
            sma50Price,
            sma50Direction,
            sma200Price,
            sma200Direction,
            obstacleRule(obstacle, priorFiftyTwoWeekHigh, majorCeiling),
            roomRule(roomAtr, roomPct),
        )
    }

    private fun priceVsSmaRule(
        key: String,
        label: String,
        close: Double,
        sma: Double?,
        isLongTerm: Boolean,
        longTermChangePct: Double?,
    ): BreakoutQualityRuleResult {
        if (sma == null) return unavailable(key, label, "The full SMA history is not available.")
        val distancePct = ((close / sma) - 1.0) * 100.0
        val belowFallingLongTerm = isLongTerm && distancePct < 0.0 &&
            (longTermChangePct ?: 0.0) < -LONG_TERM_FLAT_TOLERANCE_PCT
        val verdict = when {
            distancePct >= 0.0 -> BreakoutQualityVerdict.PASS
            belowFallingLongTerm -> BreakoutQualityVerdict.REJECT
            else -> BreakoutQualityVerdict.WAIT
        }
        return rule(
            key,
            label,
            if (isLongTerm) "Pass above · Wait below flat/rising · Reject below falling" else "Pass above · Wait below",
            "${signed(roundTo2(distancePct))}% vs ₹${roundTo2(sma)}",
            verdict,
            when (verdict) {
                BreakoutQualityVerdict.PASS -> "Price is above this trend reference."
                BreakoutQualityVerdict.REJECT -> "Price is below a falling long-term trend; normally skip."
                else -> "This average remains overhead and can act as resistance."
            },
        )
    }

    private fun smaDirectionRule(
        key: String,
        label: String,
        changePct: Double?,
        sessions: Int,
        flatTolerancePct: Double,
        flatIsAcceptable: Boolean,
    ): BreakoutQualityRuleResult {
        if (changePct == null) return unavailable(key, label, "The current and earlier SMA values are both required.")
        val direction = when {
            changePct > flatTolerancePct -> "rising"
            changePct < -flatTolerancePct -> "falling"
            else -> "flat"
        }
        val verdict = if (direction == "rising" || (flatIsAcceptable && direction == "flat")) {
            BreakoutQualityVerdict.PASS
        } else {
            BreakoutQualityVerdict.WAIT
        }
        return rule(
            key,
            label,
            "Rising >+${roundTo2(flatTolerancePct)}% · Flat ±${roundTo2(flatTolerancePct)}% · Falling below −${roundTo2(flatTolerancePct)}%",
            "$direction · ${signed(roundTo2(changePct))}% over $sessions sessions",
            verdict,
            when {
                direction == "rising" -> "The average is moving upward."
                flatIsAcceptable && direction == "flat" -> "A flat long-term average is acceptable context."
                else -> "The average is not yet rising clearly."
            },
        )
    }

    private fun obstacleRule(
        obstacle: OverheadObstacle?,
        priorFiftyTwoWeekHigh: Double?,
        majorCeiling: Double?,
    ): BreakoutQualityRuleResult = rule(
        "next-obstacle",
        "Next overhead obstacle",
        "Nearest level above price: major ceiling, prior 52W high, or 200 SMA",
        obstacle?.let { "${it.label} · ₹${roundTo2(it.price)}" } ?: "No measured obstacle above price",
        BreakoutQualityVerdict.PASS,
        "Major ₹${majorCeiling?.let(::roundTo2) ?: "—"} · prior 52W high ₹${priorFiftyTwoWeekHigh ?: "—"}.",
    )

    private fun roomRule(roomAtr: Double?, roomPct: Double?): BreakoutQualityRuleResult {
        if (roomAtr == null || roomPct == null) {
            return rule(
                "obstacle-room",
                "Room before obstacle",
                ROOM_RULE,
                "Clear measured runway",
                BreakoutQualityVerdict.PASS,
                "No measured overhead level is currently above price.",
            )
        }
        val verdict = when {
            roomAtr >= COMFORTABLE_OBSTACLE_ROOM_ATR -> BreakoutQualityVerdict.PASS
            roomAtr >= MINIMUM_OBSTACLE_ROOM_ATR -> BreakoutQualityVerdict.WAIT
            else -> BreakoutQualityVerdict.REJECT
        }
        return rule(
            "obstacle-room",
            "Room before obstacle",
            ROOM_RULE,
            "${roundTo2(roomAtr)} ATR · ${roundTo2(roomPct)}%",
            verdict,
            when (verdict) {
                BreakoutQualityVerdict.PASS -> "There is useful room before resistance."
                BreakoutQualityVerdict.WAIT -> "Room exists, but it is not generous."
                else -> "Resistance is too close for a fresh entry."
            },
        )
    }

    private fun movingAverages(candles: List<DailyCandle>): MovingAverages {
        val series = candles.toTa4jSeries("breakout-chart-context")
        val sma50Indicator = series.calculateSma(SMA50_PERIOD)
        val sma200Indicator = series.calculateSma(SMA200_PERIOD)
        val sma50 = indicatorValue(sma50Indicator, series.endIndex, SMA50_PERIOD)
        val sma200 = indicatorValue(sma200Indicator, series.endIndex, SMA200_PERIOD)
        return MovingAverages(
            sma50 = sma50?.let(::roundTo2),
            sma200 = sma200?.let(::roundTo2),
            sma50ChangePctFiveSessions = percentageChange(
                sma50,
                indicatorValue(sma50Indicator, series.endIndex - SMA50_TREND_SESSIONS, SMA50_PERIOD),
            )?.let(::roundTo2),
            sma200ChangePctTwentySessions = percentageChange(
                sma200,
                indicatorValue(sma200Indicator, series.endIndex - SMA200_TREND_SESSIONS, SMA200_PERIOD),
            )?.let(::roundTo2),
        )
    }

    private fun decisionSummary(
        decision: BreakoutQualityDecision,
        belowFallingSma200: Boolean,
        tooLittleRoom: Boolean,
        obstacle: OverheadObstacle?,
    ): String = when {
        belowFallingSma200 -> "Skip: price is below a falling 200 SMA."
        tooLittleRoom -> "Skip: the next overhead obstacle is less than 1 ATR away."
        decision == BreakoutQualityDecision.WAIT -> "Mixed context: wait for the weak or unavailable checks to improve."
        obstacle == null -> "Trend context passes and no overhead obstacle is visible in the measured levels."
        else -> "Trend context passes with at least 2 ATR of room to ${obstacle.label.lowercase()}."
    }

    private fun indicatorValue(indicator: SMAIndicator, index: Int, period: Int): Double? =
        if (index < period - 1) null else indicator.getDoubleValue(index)

    private fun percentageChange(current: Double?, earlier: Double?): Double? =
        if (current == null || earlier == null || earlier == 0.0) null else ((current / earlier) - 1.0) * 100.0

    private fun overheadObstacle(label: String, price: Double?, close: Double): OverheadObstacle? =
        price?.takeIf { it > close }?.let { OverheadObstacle(label, it) }

    private fun unavailable(key: String, label: String, explanation: String): BreakoutQualityRuleResult =
        rule(
            key,
            label,
            "Requires enough completed sessions through the selected date",
            "Unavailable",
            BreakoutQualityVerdict.UNAVAILABLE,
            explanation,
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

    private fun roundTo2(value: Double): Double = round(value * 100.0) / 100.0

    private const val SMA50_PERIOD = 50
    private const val SMA200_PERIOD = 200
    private const val SMA50_TREND_SESSIONS = 5
    private const val SMA200_TREND_SESSIONS = 20
    private const val FIFTY_TWO_WEEK_SESSIONS = 252
    private const val LONG_TERM_FLAT_TOLERANCE_PCT = 0.5
    private const val SHORT_TERM_FLAT_TOLERANCE_PCT = 0.1
    private const val MINIMUM_OBSTACLE_ROOM_ATR = 1.0
    private const val COMFORTABLE_OBSTACLE_ROOM_ATR = 2.0
    private const val ROOM_RULE = "Pass ≥2 ATR · Wait 1–1.99 ATR · Reject <1 ATR"

    private data class MovingAverages(
        val sma50: Double?,
        val sma200: Double?,
        val sma50ChangePctFiveSessions: Double?,
        val sma200ChangePctTwentySessions: Double?,
    )

    private data class OverheadObstacle(val label: String, val price: Double)
}
