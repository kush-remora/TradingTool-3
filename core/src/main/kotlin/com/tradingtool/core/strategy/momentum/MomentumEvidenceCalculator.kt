package com.tradingtool.core.strategy.momentum

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.calculateRsi
import com.tradingtool.core.technical.getNullableDouble
import com.tradingtool.core.technical.toTa4jSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.indicators.ROCIndicator
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.round

fun calculateMomentumEvidence(
    candles: List<DailyCandle>,
    asOfDate: LocalDate,
    participationThreshold: Double = DEFAULT_PARTICIPATION_THRESHOLD,
    deliveryPercentageByDate: Map<LocalDate, Double?> = emptyMap(),
): MomentumEvidence {
    require(participationThreshold > 0.0) { "participationThreshold must be greater than zero." }

    val availableCandles = candles
        .asSequence()
        .filter { candle -> !candle.candleDate.isAfter(asOfDate) }
        .distinctBy(DailyCandle::candleDate)
        .sortedBy(DailyCandle::candleDate)
        .toList()
    val currentCandle = availableCandles.lastOrNull()
    val dataStatus = when {
        currentCandle == null -> MomentumDataStatus.NO_CANDLES
        availableCandles.size < SMA200_WINDOW -> MomentumDataStatus.INSUFFICIENT_HISTORY
        else -> MomentumDataStatus.AVAILABLE
    }

    if (currentCandle == null) {
        return MomentumEvidence(
            asOfDate = asOfDate.toString(),
            currentClose = null,
            sma200 = null,
            aboveSma200 = null,
            distanceFromSma200Pct = null,
            fiftyTwoWeekHigh = null,
            distanceFromFiftyTwoWeekHighPct = null,
            thirtyDayLow = null,
            distanceFromThirtyDayLowPct = null,
            weeklyReturns = emptyList(),
            weeklyRoc = null,
            participationEvents = emptyList(),
            participationThreshold = participationThreshold,
            participationLookbackDays = PARTICIPATION_LOOKBACK_CALENDAR_DAYS.toInt(),
            dataStatus = dataStatus,
        )
    }

    val sma200 = availableCandles
        .takeLast(SMA200_WINDOW)
        .takeIf { it.size == SMA200_WINDOW }
        ?.map(DailyCandle::close)
        ?.average()
        ?.takeIf(Double::isFinite)
    val distanceFromSma200Pct = sma200
        ?.takeIf { it > 0.0 }
        ?.let { ((currentCandle.close / it) - 1.0) * 100.0 }
    val fiftyTwoWeekHigh = availableCandles
        .takeLast(FIFTY_TWO_WEEK_TRADING_SESSIONS)
        .map(DailyCandle::high)
        .filter { high -> high > 0.0 && high.isFinite() }
        .maxOrNull()
    val distanceFromFiftyTwoWeekHighPct = fiftyTwoWeekHigh
        ?.let { high -> ((currentCandle.close / high) - 1.0) * 100.0 }
    val thirtyDayLow = availableCandles
        .takeLast(THIRTY_DAY_TRADING_SESSIONS)
        .map(DailyCandle::low)
        .filter { low -> low > 0.0 && low.isFinite() }
        .minOrNull()
    val distanceFromThirtyDayLowPct = thirtyDayLow
        ?.let { low -> ((currentCandle.close / low) - 1.0) * 100.0 }
    val rsiIndicator = availableCandles.toTa4jSeries("momentum-rsi").calculateRsi(RSI_PERIOD)
    val candleIndexByDate = availableCandles.mapIndexed { index, candle -> candle.candleDate to index }.toMap()

    return MomentumEvidence(
        asOfDate = asOfDate.toString(),
        currentClose = currentCandle.close.roundTo2(),
        sma200 = sma200?.roundTo2(),
        aboveSma200 = sma200?.let { currentCandle.close > it },
        distanceFromSma200Pct = distanceFromSma200Pct?.roundTo2(),
        fiftyTwoWeekHigh = fiftyTwoWeekHigh?.roundTo2(),
        distanceFromFiftyTwoWeekHighPct = distanceFromFiftyTwoWeekHighPct?.roundTo2(),
        thirtyDayLow = thirtyDayLow?.roundTo2(),
        distanceFromThirtyDayLowPct = distanceFromThirtyDayLowPct?.roundTo2(),
        weeklyReturns = buildWeeklyReturns(availableCandles, asOfDate),
        weeklyRoc = buildWeeklyRoc(availableCandles, asOfDate),
        participationEvents = buildParticipationEvents(
            candles = availableCandles,
            asOfDate = asOfDate,
            participationThreshold = participationThreshold,
            deliveryPercentageByDate = deliveryPercentageByDate,
            rsiIndicator = rsiIndicator,
            candleIndexByDate = candleIndexByDate,
        ),
        participationThreshold = participationThreshold,
        participationLookbackDays = PARTICIPATION_LOOKBACK_CALENDAR_DAYS.toInt(),
        dataStatus = dataStatus,
    )
}

private fun buildWeeklyReturns(candles: List<DailyCandle>, asOfDate: LocalDate): List<MomentumWeeklyReturn> {
    val weeklyCloses = completedWeeklyCloses(candles, asOfDate)

    return weeklyCloses
        .zipWithNext()
        .takeLast(WEEKLY_RETURNS_TO_DISPLAY)
        .map { (previous, current) ->
            MomentumWeeklyReturn(
                weekStart = current.first.toString(),
                weekEnd = current.first.plusDays(4).toString(),
                returnPct = percentageChange(previous.second.close, current.second.close).roundTo2(),
            )
        }
}

private fun buildWeeklyRoc(candles: List<DailyCandle>, asOfDate: LocalDate): MomentumWeeklyRoc {
    val weeklyCloses = completedWeeklyCloses(candles, asOfDate)
    if (weeklyCloses.size <= WEEKLY_ROC_LOOKBACK_WEEKS) {
        return MomentumWeeklyRoc(
            lookbackWeeks = WEEKLY_ROC_LOOKBACK_WEEKS,
            currentRocPct = null,
            previousRocPct = null,
            changePctPoints = null,
            state = MomentumRocState.INSUFFICIENT_HISTORY,
        )
    }

    val series = BaseBarSeriesBuilder().withName("weekly-roc").build()
    val ist = ZoneId.of("Asia/Kolkata")
    weeklyCloses.forEach { (weekStart, candle) ->
        val close = candle.close
        series.addBar(weekStart.plusDays(4).atTime(15, 30).atZone(ist), close, close, close, close, 0.0)
    }

    val roc = ROCIndicator(ClosePriceIndicator(series), WEEKLY_ROC_LOOKBACK_WEEKS)
    val currentIndex = series.endIndex
    val previousIndex = currentIndex - 1
    val currentRaw = roc.getNullableDouble(currentIndex)
    val previousRaw = roc.getNullableDouble(previousIndex)
    val changeRaw = if (currentRaw != null && previousRaw != null) currentRaw - previousRaw else null

    return MomentumWeeklyRoc(
        lookbackWeeks = WEEKLY_ROC_LOOKBACK_WEEKS,
        currentRocPct = currentRaw?.roundTo2(),
        previousRocPct = previousRaw?.roundTo2(),
        changePctPoints = changeRaw?.roundTo2(),
        state = determineRocState(currentRaw, previousRaw, changeRaw),
    )
}

private fun completedWeeklyCloses(candles: List<DailyCandle>, asOfDate: LocalDate): List<Pair<LocalDate, DailyCandle>> = candles
    .groupBy { candle -> candle.candleDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    .toSortedMap()
    .filterKeys { weekStart -> !weekStart.plusDays(4).isAfter(asOfDate) }
    .map { (weekStart, weekCandles) -> weekStart to weekCandles.maxBy(DailyCandle::candleDate) }

private fun determineRocState(current: Double?, previous: Double?, change: Double?): MomentumRocState {
    if (current == null || previous == null || change == null) return MomentumRocState.INSUFFICIENT_HISTORY
    return when {
        change > 0.0 && previous < 0.0 -> MomentumRocState.RISING_FROM_NEGATIVE
        change > 0.0 -> MomentumRocState.RISING_POSITIVE
        change < 0.0 -> MomentumRocState.FALLING
        else -> MomentumRocState.FLAT
    }
}

private fun buildParticipationEvents(
    candles: List<DailyCandle>,
    asOfDate: LocalDate,
    participationThreshold: Double,
    deliveryPercentageByDate: Map<LocalDate, Double?>,
    rsiIndicator: RSIIndicator,
    candleIndexByDate: Map<LocalDate, Int>,
): List<MomentumParticipationEvent> {
    val eventFromDate = asOfDate.minusDays(PARTICIPATION_LOOKBACK_CALENDAR_DAYS)
    return candles.indices.mapNotNull { index ->
        val candle = candles[index]
        if (candle.candleDate.isBefore(eventFromDate)) return@mapNotNull null

        val baseline = candles
            .subList(maxOf(0, index - VOLUME_BASELINE_DAYS), index)
            .map { it.volume.toDouble() }
            .takeIf { it.size == VOLUME_BASELINE_DAYS }
            ?.average()
            ?.takeIf { it > 0.0 && it.isFinite() }
            ?: return@mapNotNull null
        val volumeRatio = candle.volume.toDouble() / baseline
        if (volumeRatio < participationThreshold) return@mapNotNull null

        MomentumParticipationEvent(
            eventDate = candle.candleDate.toString(),
            close = candle.close.roundTo2(),
            rsi14 = candleIndexByDate[candle.candleDate]
                ?.let { index -> rsiIndicator.getNullableDouble(index)?.roundTo2() },
            volume = candle.volume,
            volumeRatio = volumeRatio.roundTo2(),
            dailyReturnPct = candles.getOrNull(index - 1)
                ?.takeIf { it.close > 0.0 }
                ?.let { percentageChange(it.close, candle.close).roundTo2() },
            priceSinceEventPct = percentageChange(candle.close, candles.last().close).roundTo2(),
            deliveryPercentage = deliveryPercentageByDate[candle.candleDate]?.roundTo2(),
        )
    }.asReversed()
}

private fun percentageChange(previous: Double, current: Double): Double =
    if (previous > 0.0) ((current / previous) - 1.0) * 100.0 else 0.0

private fun Double.roundTo2(): Double = round(this * 100.0) / 100.0

private const val SMA200_WINDOW = 200
private const val VOLUME_BASELINE_DAYS = 5
const val PARTICIPATION_LOOKBACK_CALENDAR_DAYS = 90L
const val PARTICIPATION_DELIVERY_HISTORY_SESSIONS = 120
private const val FIFTY_TWO_WEEK_TRADING_SESSIONS = 252
private const val THIRTY_DAY_TRADING_SESSIONS = 30
private const val RSI_PERIOD = 14
private const val WEEKLY_RETURNS_TO_DISPLAY = 4
private const val WEEKLY_ROC_LOOKBACK_WEEKS = 3
const val DEFAULT_PARTICIPATION_THRESHOLD = 2.0
const val MOMENTUM_RSI_WARMUP_CALENDAR_DAYS = 5 * 365L
