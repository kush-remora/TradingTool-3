package com.tradingtool.core.strategy.volumeeventbacktest

import com.tradingtool.core.candle.DailyCandle
import kotlin.math.ceil

class AdaptiveRsiCalibrationCalculator : AdaptiveRsiCalibrationProvider {
    override fun calculate(
        candles: List<DailyCandle>,
        rsiValues: List<Double>,
        currentEventIndex: Int,
        config: VolumeEventConfirmationBacktestConfig,
    ): AdaptiveRsiCalibration {
        val isEarlyEntry = config.entryMode == VolumeEventEntryModes.FIVE_DAY_PAST_RSI_EARLY_ENTRY
        val confirmationLookahead = if (isEarlyEntry) 0 else config.confirmationDays
        val lastCalibrationEventIndex = currentEventIndex - confirmationLookahead - config.maxHoldingDays - 1
        val firstCalibrationEventIndex = maxOf(0, currentEventIndex - config.adaptiveRsiCalibrationDays)
        if (lastCalibrationEventIndex < firstCalibrationEventIndex) return emptyCalibration()

        val samples = mutableListOf<CalibrationSample>()
        var nextEligibleEventIndex = firstCalibrationEventIndex
        for (eventIndex in firstCalibrationEventIndex..lastCalibrationEventIndex) {
            if (eventIndex < nextEligibleEventIndex) continue

            val priorCandles = candles.previousCandles(eventIndex, config.volumeBaselineDays) ?: continue
            val priorVolumeAverage = priorCandles.map { candle -> candle.volume.toDouble() }.average()
            if (priorVolumeAverage <= 0.0 || !priorVolumeAverage.isFinite()) continue

            val eventCandle = candles[eventIndex]
            val eventRsi = rsiValues[eventIndex]
            val volumeRatio = eventCandle.volume.toDouble() / priorVolumeAverage
            if (!eventRsi.isFinite() || volumeRatio < config.volumeShockMultiplier) continue

            val priceContext = calculateLookbackPriceContext(priorCandles, eventCandle)
            if (priceContext.isBearish(config.maxLookbackDrawdownPct)) continue

            val confirmationIndex = eventIndex + config.confirmationDays
            val entryIndex = if (isEarlyEntry) eventIndex + 1 else confirmationIndex + 1
            if (entryIndex > candles.lastIndex) continue

            if (isEarlyEntry) {
                val pastRsiTrend = calculatePastRsiTrend(
                    rsiValues = rsiValues,
                    eventIndex = eventIndex,
                    lookbackDays = config.pastRsiLookbackDays,
                )
                if (pastRsiTrend == null || pastRsiTrend.changePoints <= 0.0) {
                    samples += CalibrationSample(eventRsi = eventRsi, targetHit = false)
                    continue
                }
            } else {
                val confirmationRsi = rsiValues[confirmationIndex]
                if (!confirmationRsi.isFinite() || confirmationRsi <= eventRsi) {
                    samples += CalibrationSample(eventRsi = eventRsi, targetHit = false)
                    continue
                }
            }

            val entryPrice = candles[entryIndex].open
            if (entryPrice <= 0.0 || !entryPrice.isFinite()) continue

            val outcomeEndIndex = minOf(candles.lastIndex, entryIndex + config.maxHoldingDays - 1)
            val targetPrice = entryPrice * (1.0 + config.targetPct / 100.0)
            val targetIndex = (entryIndex..outcomeEndIndex)
                .firstOrNull { index -> candles[index].high >= targetPrice }
            val exitIndex = targetIndex ?: outcomeEndIndex
            samples += CalibrationSample(eventRsi = eventRsi, targetHit = targetIndex != null)
            nextEligibleEventIndex = exitIndex + 1
        }

        return selectCalibration(samples, config)
    }

    private fun selectCalibration(
        samples: List<CalibrationSample>,
        config: VolumeEventConfirmationBacktestConfig,
    ): AdaptiveRsiCalibration {
        if (samples.size < config.adaptiveRsiMinimumSampleCount) return emptyCalibration(samples.size)

        val baselineHitRate = samples.count(CalibrationSample::targetHit) * 100.0 / samples.size
        val candidateThresholds = samples
            .map { sample -> ceil(sample.eventRsi / config.adaptiveRsiBinSize) * config.adaptiveRsiBinSize }
            .filter { threshold -> threshold in 0.0..100.0 }
            .distinct()
            .sorted()

        val selected = candidateThresholds
            .mapNotNull { threshold ->
                val included = samples.filter { sample -> sample.eventRsi <= threshold }
                if (included.size < config.adaptiveRsiMinimumSampleCount) return@mapNotNull null
                val hitRate = included.count(CalibrationSample::targetHit) * 100.0 / included.size
                if (hitRate <= baselineHitRate) return@mapNotNull null
                threshold to hitRate
            }
            .maxByOrNull { (threshold, _) -> threshold }

        return AdaptiveRsiCalibration(
            threshold = selected?.first,
            sampleCount = samples.size,
            baselineHitRatePct = baselineHitRate.roundTo2(),
            selectedHitRatePct = selected?.second?.roundTo2(),
        )
    }

    private fun emptyCalibration(sampleCount: Int = 0): AdaptiveRsiCalibration = AdaptiveRsiCalibration(
        threshold = null,
        sampleCount = sampleCount,
        baselineHitRatePct = null,
        selectedHitRatePct = null,
    )

    private data class CalibrationSample(
        val eventRsi: Double,
        val targetHit: Boolean,
    )
}

internal data class LookbackPriceContext(
    val returnPct: Double,
    val drawdownPct: Double,
) {
    fun isBearish(maxDrawdownPct: Double): Boolean = returnPct < 0.0 || drawdownPct > maxDrawdownPct
}

internal fun calculateLookbackPriceContext(
    priorCandles: List<DailyCandle>,
    eventCandle: DailyCandle,
): LookbackPriceContext {
    val priorPeakClose = priorCandles.maxOf { candle -> candle.close }
    return LookbackPriceContext(
        returnPct = ((eventCandle.close / priorCandles.first().close) - 1.0) * 100.0,
        drawdownPct = ((priorPeakClose - eventCandle.close) / priorPeakClose) * 100.0,
    )
}

internal fun List<DailyCandle>.previousCandles(
    eventIndex: Int,
    baselineDays: Int,
): List<DailyCandle>? = subList(maxOf(0, eventIndex - baselineDays), eventIndex)
    .takeIf { priorCandles -> priorCandles.size == baselineDays }

internal data class PastRsiTrend(
    val changePoints: Double,
)

internal fun calculatePastRsiTrend(
    rsiValues: List<Double>,
    eventIndex: Int,
    lookbackDays: Int,
): PastRsiTrend? {
    val firstIndex = eventIndex - lookbackDays
    if (firstIndex < 0) return null
    // The event date is excluded: compare t-5 through t-1 only.
    val values = rsiValues.subList(firstIndex, eventIndex)
    if (values.any { value -> !value.isFinite() }) return null
    return PastRsiTrend(
        changePoints = values.last() - values.first(),
    )
}

private fun Double.roundTo2(): Double = kotlin.math.round(this * 100.0) / 100.0
