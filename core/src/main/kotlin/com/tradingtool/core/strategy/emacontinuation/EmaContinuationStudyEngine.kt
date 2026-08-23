package com.tradingtool.core.strategy.emacontinuation

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

internal object EmaContinuationStudyEngine {
    fun run(
        candles: List<DailyCandle>,
        testFromDate: LocalDate,
        testToDate: LocalDate,
        watchlistKey: String,
    ): EmaContinuationStudyResponse {
        require(!testFromDate.isAfter(testToDate)) { "testFromDate must not be after testToDate." }

        val orderedCandles = candles.distinctBy(DailyCandle::candleDate).sortedBy(DailyCandle::candleDate)
        val preStudySessions = orderedCandles.count { candle -> candle.candleDate.isBefore(testFromDate) }
        require(preStudySessions >= MINIMUM_WARMUP_SESSIONS) {
            "At least $MINIMUM_WARMUP_SESSIONS completed sessions before $testFromDate are required for the 200 SMA trend filter."
        }

        val points = buildIndicatorPoints(orderedCandles)
        val setupIndices = points.indices.filter { index ->
            val point = points[index]
            point.date >= testFromDate && point.date <= testToDate &&
                isTrendQualified(index, points) && point.isUpwardEmaCross && point.volumeConfirmed
        }
        val signals = mutableListOf<EmaContinuationSignal>()
        val usedSetupIndices = mutableSetOf<Int>()

        for (index in orderedCandles.indices) {
            val point = points[index]
            if (point.date.isBefore(testFromDate) || point.date.isAfter(testToDate)) continue
            if (!isTrendQualified(index, points)) continue

            if (index in setupIndices) {
                signals += buildEmaCrossSignal(index, points)
            }

            val setupIndex = setupIndices.lastOrNull { candidate -> candidate < index }
            if (setupIndex == null || setupIndex in usedSetupIndices) continue
            val base = findBase(index, setupIndex, points) ?: continue
            if (!isBaseBreakout(point, base)) continue

            signals += buildBaseBreakoutSignal(
                index = index,
                setupIndex = setupIndex,
                base = base,
                points = points,
            )
            usedSetupIndices += setupIndex
        }

        val breakoutDates = signals
            .filter { signal -> signal.type == EmaContinuationSignalType.BASE_BREAKOUT }
            .mapTo(mutableSetOf()) { signal -> signal.date }
        val setupIndexSet = setupIndices.toSet()
        val testedPoints = points.filter { point -> point.date >= testFromDate && point.date <= testToDate }
        require(testedPoints.isNotEmpty()) { "No completed daily candles are available in the requested study window." }
        val dailyCandles = points.mapIndexedNotNull { index, point ->
            if (point.date < testFromDate || point.date > testToDate) return@mapIndexedNotNull null
            val baseBreakout = breakoutDates.contains(point.date.toString())
            EmaContinuationDailyCandle(
                date = point.date.toString(),
                open = point.candle.open,
                high = point.candle.high,
                low = point.candle.low,
                close = point.candle.close,
                volume = point.candle.volume,
                closeNearHighPct = point.closeNearHighPct,
                closeAboveEma20Atr = point.closeAboveEma20Atr,
                volumeVs20SessionAverageRatio = point.volumeVs20SessionAverageRatio,
                volumeConfirmed = point.volumeConfirmed,
                ema10 = point.ema10,
                ema20 = point.ema20,
                sma200 = point.sma200,
                emaProximityPct = point.ema10?.let { ema -> percentageDistance(point.candle.close, ema) },
                emaCross = setupIndexSet.contains(index),
                baseBreakout = baseBreakout,
            )
        }

        return EmaContinuationStudyResponse(
            symbol = orderedCandles.first().symbol,
            watchlistKey = watchlistKey,
            testedFromDate = testFromDate.toString(),
            testedToDate = testedPoints.last().date.toString(),
            warmupSessions = preStudySessions,
            minimumBaseSessions = MINIMUM_BASE_SESSIONS,
            maximumBaseSessions = MAXIMUM_BASE_SESSIONS,
            maximumBaseWidthPct = MAXIMUM_BASE_WIDTH_PCT,
            atrPeriodSessions = ATR_PERIOD_SESSIONS,
            volumeLookbackSessions = VOLUME_LOOKBACK_SESSIONS,
            minimumVolumeRatio = MINIMUM_VOLUME_RATIO,
            entryRule = "Strategy 1 — EMA cross: buy at the next tradable session open when the daily close crosses above both EMAs, price is above a rising SMA200, and setup-day volume is at least 1.5x the previous 20-session average. Strategy 2 — base breakout: after a prior EMA setup, buy at the next open when a daily close breaks above the prior 5–15 session base high and remains above both EMAs. Breakout-day volume is context, not a gate.",
            baseRule = "Base uses the prior 5–15 completed sessions and must be no wider than 12% from low to high. An EMA20 touch alone is not an entry.",
            summary = EmaContinuationStudySummary(
                emaCrossCount = setupIndices.size,
                baseBreakoutCount = signals.count { signal -> signal.type == EmaContinuationSignalType.BASE_BREAKOUT },
                latestSignalDate = signals.lastOrNull()?.date,
                latestSignalType = signals.lastOrNull()?.type,
            ),
            candles = dailyCandles,
            signals = signals,
            targetPerformance = buildTargetPerformance(signals),
        )
    }

    internal fun isBaseBreakout(
        current: IndicatorPoint,
        base: BaseRange,
    ): Boolean = current.close > base.high &&
        current.ema10 != null && current.ema20 != null &&
        current.close > current.ema10 && current.close > current.ema20

    internal fun findBase(
        breakoutIndex: Int,
        setupIndex: Int,
        points: List<IndicatorPoint>,
    ): BaseRange? {
        val maximumLength = minOf(MAXIMUM_BASE_SESSIONS, breakoutIndex - setupIndex - 1)
        if (maximumLength < MINIMUM_BASE_SESSIONS) return null

        for (length in maximumLength downTo MINIMUM_BASE_SESSIONS) {
            val startIndex = breakoutIndex - length
            if (startIndex <= setupIndex) continue
            val basePoints = points.subList(startIndex, breakoutIndex)
            val high = basePoints.maxOf(IndicatorPoint::high)
            val low = basePoints.minOf(IndicatorPoint::low)
            val widthPct = ((high - low) / low) * 100.0
            if (widthPct <= MAXIMUM_BASE_WIDTH_PCT) {
                return BaseRange(
                    startDate = points[startIndex].date,
                    endDate = points[breakoutIndex - 1].date,
                    high = high,
                    low = low,
                    widthPct = widthPct,
                )
            }
        }
        return null
    }

    internal fun isUpwardEmaCross(
        previousClose: Double,
        currentClose: Double,
        previousEma10: Double,
        previousEma20: Double,
        currentEma10: Double,
        currentEma20: Double,
    ): Boolean = currentClose > currentEma10 && currentClose > currentEma20 &&
        (previousClose <= previousEma10 || previousClose <= previousEma20)

    internal fun isVolumeConfirmed(
        currentVolume: Long,
        previousVolumes: List<Long>,
        minimumRatio: Double = MINIMUM_VOLUME_RATIO,
    ): Boolean {
        if (previousVolumes.size < VOLUME_LOOKBACK_SESSIONS || currentVolume <= 0L) return false
        val baseline = previousVolumes.takeLast(VOLUME_LOOKBACK_SESSIONS).average()
        return baseline > 0.0 && currentVolume.toDouble() / baseline >= minimumRatio
    }

    internal fun firstTargetHitDays(
        entryPrice: Double,
        futureHighs: List<Double>,
        targetPct: Double,
    ): Int? {
        require(entryPrice > 0.0) { "entryPrice must be positive." }
        require(targetPct >= 0.0) { "targetPct must not be negative." }
        val targetPrice = entryPrice * (1.0 + targetPct / 100.0)
        return futureHighs.indexOfFirst { high -> high >= targetPrice }
            .takeIf { index -> index >= 0 }
            ?.plus(1)
    }

    internal fun closeNearHighPct(high: Double, low: Double, close: Double): Double? {
        val range = high - low
        return range.takeIf { value -> value > 0.0 }?.let { value -> ((close - low) / value) * 100.0 }
    }

    internal fun closeAboveEma20Atr(close: Double, ema20: Double?, atr: Double?): Double? =
        ema20?.let { ema -> atr?.takeIf { value -> value > 0.0 }?.let { value -> (close - ema) / value } }

    private fun isTrendQualified(index: Int, points: List<IndicatorPoint>): Boolean {
        val current = points[index]
        val earlier = points.getOrNull(index - SMA_TREND_LOOKBACK_SESSIONS)
        return current.sma200 != null && earlier?.sma200 != null &&
            current.close > current.sma200 && current.sma200 > earlier.sma200
    }

    private fun buildEmaCrossSignal(
        index: Int,
        points: List<IndicatorPoint>,
    ): EmaContinuationSignal {
        val point = points[index]
        val entry = points.getOrNull(index + 1)
        return EmaContinuationSignal(
            type = EmaContinuationSignalType.EMA_CROSS,
            date = point.date.toString(),
            setupDate = point.date.toString(),
            close = point.close,
            ema10 = requireNotNull(point.ema10),
            ema20 = requireNotNull(point.ema20),
            sma200 = requireNotNull(point.sma200),
            closeNearHighPct = point.closeNearHighPct,
            closeAboveEma20Atr = point.closeAboveEma20Atr,
            volumeVs20SessionAverageRatio = point.volumeVs20SessionAverageRatio,
            volumeConfirmed = point.volumeConfirmed,
            entryDate = entry?.date?.toString(),
            entryPrice = entry?.candle?.open,
            daysTo5PctTarget = targetDays(points, index + 1, targetPct = 5.0),
            daysTo10PctTarget = targetDays(points, index + 1, targetPct = 10.0),
            daysTo20PctTarget = targetDays(points, index + 1, targetPct = 20.0),
        )
    }

    private fun buildBaseBreakoutSignal(
        index: Int,
        setupIndex: Int,
        base: BaseRange,
        points: List<IndicatorPoint>,
    ): EmaContinuationSignal {
        val point = points[index]
        val entry = points.getOrNull(index + 1)
        return EmaContinuationSignal(
            type = EmaContinuationSignalType.BASE_BREAKOUT,
            date = point.date.toString(),
            setupDate = points[setupIndex].date.toString(),
            baseStartDate = base.startDate.toString(),
            baseEndDate = base.endDate.toString(),
            baseHigh = base.high,
            baseLow = base.low,
            baseWidthPct = base.widthPct,
            close = point.close,
            ema10 = requireNotNull(point.ema10),
            ema20 = requireNotNull(point.ema20),
            sma200 = requireNotNull(point.sma200),
            closeNearHighPct = point.closeNearHighPct,
            closeAboveEma20Atr = point.closeAboveEma20Atr,
            volumeVs20SessionAverageRatio = point.volumeVs20SessionAverageRatio,
            volumeConfirmed = point.volumeConfirmed,
            entryDate = entry?.date?.toString(),
            entryPrice = entry?.candle?.open,
            daysTo5PctTarget = targetDays(points, index + 1, targetPct = 5.0),
            daysTo10PctTarget = targetDays(points, index + 1, targetPct = 10.0),
            daysTo20PctTarget = targetDays(points, index + 1, targetPct = 20.0),
        )
    }

    private fun targetDays(points: List<IndicatorPoint>, entryIndex: Int, targetPct: Double): Int? {
        val entryPrice = points.getOrNull(entryIndex)?.candle?.open ?: return null
        return firstTargetHitDays(
            entryPrice = entryPrice,
            futureHighs = points.drop(entryIndex).map(IndicatorPoint::high),
            targetPct = targetPct,
        )
    }

    private fun buildTargetPerformance(signals: List<EmaContinuationSignal>): List<EmaContinuationTargetPerformance> =
        listOf(5.0, 10.0, 20.0).map { targetPct ->
            val daysToTarget = signals.map { signal ->
                when (targetPct) {
                    5.0 -> signal.daysTo5PctTarget
                    10.0 -> signal.daysTo10PctTarget
                    else -> signal.daysTo20PctTarget
                }
            }
            val hits = daysToTarget.filterNotNull()
            EmaContinuationTargetPerformance(
                targetPct = targetPct,
                observations = signals.count { signal -> signal.entryPrice != null },
                hitCount = hits.size,
                hitRatePct = signals.takeIf { entries -> entries.isNotEmpty() }?.let { entries ->
                    hits.size * 100.0 / entries.size
                },
                averageDaysToHit = hits.takeIf { values -> values.isNotEmpty() }?.average(),
            )
        }

    private fun buildIndicatorPoints(candles: List<DailyCandle>): List<IndicatorPoint> {
        val ema10 = calculateEma(candles, 10)
        val ema20 = calculateEma(candles, 20)
        val sma200 = calculateSma(candles, 200)
        val atr14 = calculateAtr(candles, ATR_PERIOD_SESSIONS)
        return candles.indices.map { index ->
            val previous = index.takeIf { value -> value > 0 }?.let { value ->
                IndicatorPoint(
                    candle = candles[value - 1],
                    ema10 = ema10[value - 1],
                    ema20 = ema20[value - 1],
                    sma200 = sma200[value - 1],
                    volumeVs20SessionAverageRatio = null,
                    volumeConfirmed = false,
                    isUpwardEmaCross = false,
                )
            }
            val currentEma10 = ema10[index]
            val currentEma20 = ema20[index]
            val previousVolumes = candles.subList(maxOf(0, index - VOLUME_LOOKBACK_SESSIONS), index)
                .map(DailyCandle::volume)
            val volumeRatio = volumeRatio(candles[index].volume, previousVolumes)
            IndicatorPoint(
                candle = candles[index],
                ema10 = currentEma10,
                ema20 = currentEma20,
                sma200 = sma200[index],
                closeNearHighPct = closeNearHighPct(candles[index].high, candles[index].low, candles[index].close),
                closeAboveEma20Atr = closeAboveEma20Atr(candles[index].close, currentEma20, atr14[index]),
                volumeVs20SessionAverageRatio = volumeRatio,
                volumeConfirmed = isVolumeConfirmed(candles[index].volume, previousVolumes),
                isUpwardEmaCross = previous?.ema10 != null && previous.ema20 != null &&
                    currentEma10 != null && currentEma20 != null && isUpwardEmaCross(
                        previousClose = previous.close,
                        currentClose = candles[index].close,
                        previousEma10 = previous.ema10,
                        previousEma20 = previous.ema20,
                        currentEma10 = currentEma10,
                        currentEma20 = currentEma20,
                    ),
            )
        }
    }

    private fun calculateEma(candles: List<DailyCandle>, period: Int): List<Double?> {
        val multiplier = 2.0 / (period + 1)
        var previous: Double? = null
        return candles.map { candle ->
            val current = previous?.let { value -> value + multiplier * (candle.close - value) } ?: candle.close
            previous = current
            current
        }
    }

    private fun calculateSma(candles: List<DailyCandle>, period: Int): List<Double?> {
        var rollingSum = 0.0
        return candles.indices.map { index ->
            rollingSum += candles[index].close
            if (index >= period) rollingSum -= candles[index - period].close
            if (index + 1 < period) null else rollingSum / period
        }
    }

    private fun calculateAtr(candles: List<DailyCandle>, period: Int): List<Double?> {
        val trueRanges = candles.indices.map { index ->
            val candle = candles[index]
            val previousClose = candles.getOrNull(index - 1)?.close
            maxOf(
                candle.high - candle.low,
                previousClose?.let { close -> kotlin.math.abs(candle.high - close) } ?: 0.0,
                previousClose?.let { close -> kotlin.math.abs(candle.low - close) } ?: 0.0,
            )
        }
        var previousAtr: Double? = null
        return trueRanges.indices.map { index ->
            if (index + 1 < period) {
                null
            } else if (previousAtr == null) {
                trueRanges.subList(index - period + 1, index + 1).average().also { value -> previousAtr = value }
            } else {
                (((previousAtr!! * (period - 1)) + trueRanges[index]) / period).also { value -> previousAtr = value }
            }
        }
    }

    private fun percentageDistance(value: Double, reference: Double): Double = ((value / reference) - 1.0) * 100.0

    private fun volumeRatio(currentVolume: Long, previousVolumes: List<Long>): Double? {
        if (previousVolumes.size < VOLUME_LOOKBACK_SESSIONS) return null
        val baseline = previousVolumes.takeLast(VOLUME_LOOKBACK_SESSIONS).average()
        return baseline.takeIf { value -> value > 0.0 }?.let { value -> currentVolume.toDouble() / value }
    }

    internal data class IndicatorPoint(
        val candle: DailyCandle,
        val ema10: Double?,
        val ema20: Double?,
        val sma200: Double?,
        val closeNearHighPct: Double? = null,
        val closeAboveEma20Atr: Double? = null,
        val volumeVs20SessionAverageRatio: Double? = null,
        val volumeConfirmed: Boolean = false,
        val isUpwardEmaCross: Boolean,
    ) {
        val date: LocalDate get() = candle.candleDate
        val close: Double get() = candle.close
        val high: Double get() = candle.high
        val low: Double get() = candle.low
    }

    internal data class BaseRange(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val high: Double,
        val low: Double,
        val widthPct: Double,
    )

    private const val MINIMUM_WARMUP_SESSIONS = 220
    private const val SMA_TREND_LOOKBACK_SESSIONS = 20
    const val ATR_PERIOD_SESSIONS = 14
    const val VOLUME_LOOKBACK_SESSIONS = 20
    const val MINIMUM_BASE_SESSIONS = 5
    const val MAXIMUM_BASE_SESSIONS = 15
    const val MAXIMUM_BASE_WIDTH_PCT = 12.0
    const val MINIMUM_VOLUME_RATIO = 1.5
}
