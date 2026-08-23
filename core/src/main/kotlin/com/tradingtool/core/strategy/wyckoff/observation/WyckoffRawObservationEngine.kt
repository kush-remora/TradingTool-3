package com.tradingtool.core.strategy.wyckoff.observation

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.roundTo2
import java.time.LocalDate
import kotlin.math.abs

class WyckoffRawObservationEngine {

    fun buildStock(
        symbol: String,
        companyName: String,
        watchlists: List<String>,
        candles: List<DailyCandle>,
        asOfDate: LocalDate,
        lookbackCalendarDays: Int,
    ): WyckoffRawObservationStock? {
        val displayFromDate = asOfDate.minusDays(lookbackCalendarDays.toLong())
        val orderedCandles = candles
            .asSequence()
            .filter { candle -> candle.candleDate <= asOfDate }
            .sortedBy { candle -> candle.candleDate }
            .toList()
        val displayCandles = orderedCandles.filter { candle -> candle.candleDate >= displayFromDate }

        if (displayCandles.isEmpty()) {
            return null
        }

        val rows = orderedCandles.mapIndexedNotNull { index, candle ->
            if (candle.candleDate >= displayFromDate) {
                buildRow(orderedCandles, index, candle)
            } else {
                null
            }
        }
        val annotatedRows = annotateRows(rows)
        val validAtrPcts = rows.mapNotNull { row -> row.atrPct }
        val validVolumes = displayCandles.map { candle -> candle.volume.toDouble() }.filter { volume -> volume > 0.0 }
        val firstClose = displayCandles.first().close.takeIf { close -> close > 0.0 }
        val latestClose = displayCandles.last().close.takeIf { close -> close > 0.0 }

        return WyckoffRawObservationStock(
            symbol = symbol,
            companyName = companyName,
            watchlists = watchlists,
            startDate = displayCandles.first().candleDate.toString(),
            endDate = displayCandles.last().candleDate.toString(),
            firstClose = firstClose?.roundTo2(),
            latestClose = latestClose?.roundTo2(),
            periodReturnPct = calculatePercentageChange(firstClose, latestClose)?.roundTo2(),
            periodHigh = displayCandles.maxOfOrNull { candle -> candle.high }?.roundTo2(),
            periodLow = displayCandles.minOfOrNull { candle -> candle.low }?.roundTo2(),
            averageAtrPct = validAtrPcts.averageOrNull()?.roundTo2(),
            lowestAtrPct = validAtrPcts.minOrNull()?.roundTo2(),
            averageVolume = validVolumes.averageOrNull()?.roundTo2(),
            periods = buildPeriods(annotatedRows),
            observations = annotatedRows,
        )
    }

    private fun buildRow(
        candles: List<DailyCandle>,
        index: Int,
        candle: DailyCandle,
    ): WyckoffRawObservationRow {
        val previousCandle = candles.getOrNull(index - 1)
        val trueRange = calculateTrueRange(candle, previousCandle)
        val atr14 = calculateAtr(candles, index, ATR_PERIOD)
        val priorVolumes = previousValues(candles, index, VOLUME_LOOKBACK)
            .map { previous -> previous.volume.toDouble() }
            .filter { volume -> volume > 0.0 }
        val priceTwentySessionsAgo = candles.getOrNull(index - PRICE_LOOKBACK)?.close
        val twentySessionCandles = candles.subList(
            (index - PRICE_LOOKBACK + 1).coerceAtLeast(0),
            index + 1,
        )
        val priorTwentySessionCandles = previousValues(candles, index, PRICE_LOOKBACK)

        return WyckoffRawObservationRow(
            date = candle.candleDate.toString(),
            open = candle.open.roundTo2(),
            high = candle.high.roundTo2(),
            low = candle.low.roundTo2(),
            close = candle.close.roundTo2(),
            volume = candle.volume,
            closeChangePct = previousCandle
                ?.let { previous -> calculatePercentageChange(previous.close, candle.close) }
                ?.roundTo2(),
            rangePct = if (candle.close > 0.0) {
                (((candle.high - candle.low) / candle.close) * 100.0).roundTo2()
            } else {
                null
            },
            trueRange = trueRange?.roundTo2(),
            atr14 = atr14?.roundTo2(),
            atrPct = atr14
                ?.takeIf { atr -> candle.close > 0.0 }
                ?.let { atr -> (atr / candle.close) * 100.0 }
                ?.roundTo2(),
            movementVsAtr = if (atr14 != null && atr14 > 0.0 && trueRange != null) {
                (trueRange / atr14).roundTo2()
            } else {
                null
            },
            volumeVs20dAverage = if (priorVolumes.isNotEmpty() && priorVolumes.average() > 0.0) {
                (candle.volume / priorVolumes.average()).roundTo2()
            } else {
                null
            },
            priceChange20dPct = priceTwentySessionsAgo
                ?.let { previousClose -> calculatePercentageChange(previousClose, candle.close) }
                ?.roundTo2(),
            twentyDayRangePct = calculateRangePct(twentySessionCandles),
            priorTwentyDayHigh = priorTwentySessionCandles
                .takeIf { values -> values.size == PRICE_LOOKBACK }
                ?.maxOfOrNull { previous -> previous.high }
                ?.roundTo2(),
            phase = WyckoffRawObservationPhase.UNCLASSIFIED,
            annotation = null,
        )
    }

    private fun annotateRows(rows: List<WyckoffRawObservationRow>): List<WyckoffRawObservationRow> {
        var phaseDLevel: Double? = null
        var phaseDRemainingSessions = 0

        return rows.map { row ->
            val breakout = isPhaseDBreakout(row)
            val currentPhaseDLevel = phaseDLevel
            val phaseDActive = currentPhaseDLevel != null &&
                phaseDRemainingSessions > 0 &&
                row.close >= currentPhaseDLevel

            when {
                breakout -> {
                    phaseDLevel = row.priorTwentyDayHigh
                    phaseDRemainingSessions = PHASE_D_FOLLOW_THROUGH_SESSIONS
                    row.copy(
                        phase = WyckoffRawObservationPhase.POSSIBLE_PHASE_D,
                        annotation = phaseDBreakoutAnnotation(row),
                    )
                }
                phaseDActive -> {
                    phaseDRemainingSessions -= 1
                    row.copy(
                        phase = WyckoffRawObservationPhase.POSSIBLE_PHASE_D,
                        annotation = "Price remains above the possible launch level; inspect follow-through.",
                    )
                }
                else -> {
                    phaseDLevel = null
                    phaseDRemainingSessions = 0
                    classifyNonPhaseDRow(row)
                }
            }
        }
    }

    private fun classifyNonPhaseDRow(row: WyckoffRawObservationRow): WyckoffRawObservationRow {
        val priceChange = row.priceChange20dPct
        val rangePct = row.twentyDayRangePct
        return when {
            priceChange != null && priceChange <= -DECLINE_THRESHOLD_PCT -> row.copy(
                phase = WyckoffRawObservationPhase.PRIOR_DECLINE,
                annotation = "Price is down ${formatPercent(priceChange)} over 20 sessions.",
            )
            priceChange != null &&
                rangePct != null &&
                abs(priceChange) <= ACCUMULATION_NET_CHANGE_PCT &&
                rangePct <= ACCUMULATION_RANGE_PCT -> row.copy(
                phase = WyckoffRawObservationPhase.CANDIDATE_ACCUMULATION,
                annotation = "Price is contained in a ${formatPercent(rangePct)} 20-session range; inspect for accumulation context.",
            )
            priceChange != null &&
                priceChange >= ORDERLY_ADVANCE_THRESHOLD_PCT &&
                row.movementVsAtr != null &&
                row.movementVsAtr <= ORDERLY_RANGE_TO_ATR_MAX &&
                row.volumeVs20dAverage != null &&
                row.volumeVs20dAverage <= ORDERLY_VOLUME_MULTIPLE_MAX -> row.copy(
                phase = WyckoffRawObservationPhase.ORDERLY_ADVANCE,
                annotation = "Price is advancing without an unusually large range or volume reading.",
            )
            else -> row
        }
    }

    private fun isPhaseDBreakout(row: WyckoffRawObservationRow): Boolean {
        val priorHigh = row.priorTwentyDayHigh ?: return false
        val hasExpansion = (row.movementVsAtr ?: 0.0) >= PHASE_D_RANGE_TO_ATR_MIN ||
            (row.volumeVs20dAverage ?: 0.0) >= PHASE_D_VOLUME_MULTIPLE_MIN
        return row.close > priorHigh && hasExpansion
    }

    private fun phaseDBreakoutAnnotation(row: WyckoffRawObservationRow): String {
        val rangeMultiple = row.movementVsAtr?.let { value -> "${value}x ATR range" } ?: "larger range"
        val volumeMultiple = row.volumeVs20dAverage?.let { value -> "${value}x average volume" } ?: "volume expansion"
        return "Closed above the prior 20-session high with $rangeMultiple and $volumeMultiple; inspect as a possible Phase D launch."
    }

    private fun buildPeriods(rows: List<WyckoffRawObservationRow>): List<WyckoffRawObservationPeriod> {
        val periods = mutableListOf<WyckoffRawObservationPeriod>()
        var startIndex = 0

        while (startIndex < rows.size) {
            val phase = rows[startIndex].phase
            var endIndex = startIndex
            while (endIndex + 1 < rows.size && rows[endIndex + 1].phase == phase) {
                endIndex += 1
            }
            val observationCount = endIndex - startIndex + 1
            val shouldShow = phase == WyckoffRawObservationPhase.POSSIBLE_PHASE_D ||
                observationCount >= MIN_PERIOD_SESSIONS
            if (phase != WyckoffRawObservationPhase.UNCLASSIFIED && shouldShow) {
                periods += WyckoffRawObservationPeriod(
                    phase = phase,
                    startDate = rows[startIndex].date,
                    endDate = rows[endIndex].date,
                    observationCount = observationCount,
                    explanation = periodExplanation(phase),
                )
            }
            startIndex = endIndex + 1
        }
        return periods
    }

    private fun periodExplanation(phase: WyckoffRawObservationPhase): String = when (phase) {
        WyckoffRawObservationPhase.PRIOR_DECLINE -> "A preceding price decline. Review the higher-timeframe chart for context."
        WyckoffRawObservationPhase.CANDIDATE_ACCUMULATION -> "A stable price area with a contained 20-session range. This is only a candidate for accumulation."
        WyckoffRawObservationPhase.ORDERLY_ADVANCE -> "A positive price move without an unusually large daily range or volume reading."
        WyckoffRawObservationPhase.POSSIBLE_PHASE_D -> "A breakout and follow-through area that may be Phase D. Confirm the structure and volume manually."
        WyckoffRawObservationPhase.UNCLASSIFIED -> "No simple observation label was assigned."
    }

    private fun calculateRangePct(candles: List<DailyCandle>): Double? {
        if (candles.size < PRICE_LOOKBACK) return null
        val lowestLow = candles.minOfOrNull { candle -> candle.low } ?: return null
        val highestHigh = candles.maxOfOrNull { candle -> candle.high } ?: return null
        return if (lowestLow > 0.0) {
            (((highestHigh - lowestLow) / lowestLow) * 100.0).roundTo2()
        } else {
            null
        }
    }

    private fun formatPercent(value: Double): String {
        return "${if (value >= 0.0) "+" else ""}${value.roundTo2()}%"
    }

    private fun calculateAtr(candles: List<DailyCandle>, index: Int, period: Int): Double? {
        if (index < period - 1) {
            return null
        }
        val trueRanges = (index - period + 1..index).mapNotNull { currentIndex ->
            calculateTrueRange(candles[currentIndex], candles.getOrNull(currentIndex - 1))
        }
        return trueRanges.takeIf { values -> values.size == period }?.average()
    }

    private fun calculateTrueRange(candle: DailyCandle, previousCandle: DailyCandle?): Double? {
        if (!candle.high.isFinite() || !candle.low.isFinite() || candle.high < candle.low) {
            return null
        }
        if (previousCandle == null || previousCandle.close <= 0.0 || !previousCandle.close.isFinite()) {
            return candle.high - candle.low
        }
        return maxOf(
            candle.high - candle.low,
            abs(candle.high - previousCandle.close),
            abs(candle.low - previousCandle.close),
        )
    }

    private fun previousValues(candles: List<DailyCandle>, index: Int, count: Int): List<DailyCandle> {
        if (index <= 0) return emptyList()
        val start = (index - count).coerceAtLeast(0)
        return candles.subList(start, index)
    }

    private fun calculatePercentageChange(from: Double?, to: Double?): Double? {
        if (from == null || to == null || from <= 0.0 || !from.isFinite() || !to.isFinite()) {
            return null
        }
        return ((to - from) / from) * 100.0
    }

    private fun Iterable<Double>.averageOrNull(): Double? {
        val values = toList()
        return values.takeIf { items -> items.isNotEmpty() }?.average()
    }

    companion object {
        private const val ATR_PERIOD = 14
        private const val VOLUME_LOOKBACK = 20
        private const val PRICE_LOOKBACK = 20
        private const val DECLINE_THRESHOLD_PCT = 10.0
        private const val ACCUMULATION_NET_CHANGE_PCT = 8.0
        private const val ACCUMULATION_RANGE_PCT = 15.0
        private const val ORDERLY_ADVANCE_THRESHOLD_PCT = 5.0
        private const val ORDERLY_RANGE_TO_ATR_MAX = 1.5
        private const val ORDERLY_VOLUME_MULTIPLE_MAX = 2.0
        private const val PHASE_D_RANGE_TO_ATR_MIN = 1.5
        private const val PHASE_D_VOLUME_MULTIPLE_MIN = 1.5
        private const val PHASE_D_FOLLOW_THROUGH_SESSIONS = 15
        private const val MIN_PERIOD_SESSIONS = 5
    }
}
