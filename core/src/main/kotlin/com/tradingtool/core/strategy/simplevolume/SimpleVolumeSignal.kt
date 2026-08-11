package com.tradingtool.core.strategy.simplevolume

import com.tradingtool.core.candle.DailyCandle

const val DEFAULT_SIMPLE_VOLUME_AVERAGE_LENGTH: Int = 50
const val DEFAULT_SIMPLE_VOLUME_POCKET_PIVOT_LOOKBACK: Int = 10
const val DEFAULT_SIMPLE_VOLUME_DRY_FRACTION: Double = 0.20
const val DEFAULT_SIMPLE_VOLUME_BULL_SNORT_MULTIPLIER: Double = 3.0
const val DEFAULT_SIMPLE_VOLUME_BULL_SNORT_HIGH_FRACTION: Double = 0.65

enum class SimpleVolumeClassification {
    POCKET_PIVOT,
    HIGH_VOLUME_UP,
    HIGH_VOLUME_DOWN,
    DRY,
    NORMAL,
    INSUFFICIENT_DATA,
}

data class SimpleVolumeSettings(
    val averageLength: Int = DEFAULT_SIMPLE_VOLUME_AVERAGE_LENGTH,
    val pocketPivotLookback: Int = DEFAULT_SIMPLE_VOLUME_POCKET_PIVOT_LOOKBACK,
    val dryFraction: Double = DEFAULT_SIMPLE_VOLUME_DRY_FRACTION,
    val bullSnortMultiplier: Double = DEFAULT_SIMPLE_VOLUME_BULL_SNORT_MULTIPLIER,
    val bullSnortHighFraction: Double = DEFAULT_SIMPLE_VOLUME_BULL_SNORT_HIGH_FRACTION,
)

data class SimpleVolumeSignal(
    val classification: SimpleVolumeClassification,
    val averageVolume: Double?,
    val relativeVolume: Double?,
    val pocketPivot: Boolean,
    val bullSnort: Boolean,
)

/**
 * Calculates the compact, volume-only signals used by the stock review chart.
 *
 * Moving averages deliberately use completed sessions before the current candle. This avoids
 * letting a current volume spike dilute its own baseline and keeps the result usable while a
 * live candle is still forming.
 */
fun calculateSimpleVolumeSignals(
    candles: List<DailyCandle>,
    settings: SimpleVolumeSettings = SimpleVolumeSettings(),
): List<SimpleVolumeSignal> {
    require(settings.averageLength > 0) { "averageLength must be positive" }
    require(settings.pocketPivotLookback > 0) { "pocketPivotLookback must be positive" }
    require(settings.dryFraction in 0.0..1.0) { "dryFraction must be between 0 and 1" }
    require(settings.bullSnortMultiplier > 0.0) { "bullSnortMultiplier must be positive" }
    require(settings.bullSnortHighFraction in 0.0..1.0) { "bullSnortHighFraction must be between 0 and 1" }

    return candles.mapIndexed { index, candle ->
        val priorAverageVolumes = candles
            .subList(maxOf(0, index - settings.averageLength), index)
            .map { prior -> prior.volume.toDouble() }
        val averageVolume = priorAverageVolumes
            .takeIf { values -> values.size == settings.averageLength }
            ?.average()
            ?.takeIf { average -> average.isFinite() && average > 0.0 }
        val relativeVolume = averageVolume?.let { average -> candle.volume / average }

        val priorCandles = candles.subList(maxOf(0, index - settings.pocketPivotLookback), index)
        val highestPriorDownVolume = priorCandles
            .filter { prior -> prior.close < prior.open }
            .maxOfOrNull { prior -> prior.volume }
        val pocketPivot = candle.close > candle.open &&
            highestPriorDownVolume != null &&
            candle.volume > highestPriorDownVolume

        val closeLocation = candle.high
            .minus(candle.low)
            .takeIf { spread -> spread > 0.0 }
            ?.let { spread -> (candle.close - candle.low) / spread }
        val bullSnort = averageVolume != null &&
            candle.volume >= averageVolume * settings.bullSnortMultiplier &&
            closeLocation != null &&
            closeLocation >= settings.bullSnortHighFraction &&
            index > 0 &&
            candle.close > candles[index - 1].close

        val classification = when {
            pocketPivot -> SimpleVolumeClassification.POCKET_PIVOT
            averageVolume == null -> SimpleVolumeClassification.INSUFFICIENT_DATA
            candle.volume <= averageVolume * settings.dryFraction -> SimpleVolumeClassification.DRY
            candle.close > candle.open && candle.volume > averageVolume -> SimpleVolumeClassification.HIGH_VOLUME_UP
            candle.close < candle.open && candle.volume > averageVolume -> SimpleVolumeClassification.HIGH_VOLUME_DOWN
            else -> SimpleVolumeClassification.NORMAL
        }

        SimpleVolumeSignal(
            classification = classification,
            averageVolume = averageVolume,
            relativeVolume = relativeVolume,
            pocketPivot = pocketPivot,
            bullSnort = bullSnort,
        )
    }
}
