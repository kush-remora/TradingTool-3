package com.tradingtool.core.strategy.remora

import org.ta4j.core.BarSeries

/**
 * Detects Remora (institutional accumulation/distribution) signals from a daily BarSeries.
 *
 * Signal fires when:
 *   - Volume >= 1.5x the 20-day average (computed from the 20 bars before today)
 *   - Price barely moved: abs(dailyChangePct) <= 1.5%
 *   - The pattern holds for at least 2 consecutive days
 *
 * Signal types:
 *   ACCUMULATION — price flat-to-up (priceChangePct >= -0.5%): institutions quietly buying
 *   DISTRIBUTION — price flat-to-down (priceChangePct < -0.5%): institutions quietly selling
 */
object RemoraSignalCalculator {

    private const val MIN_BARS_REQUIRED = 22         // 20 for avg baseline + at least 2 signal days
    private const val VOLUME_RATIO_THRESHOLD = 1.5
    private const val MAX_PRICE_MOVE_PCT = 1.5
    private const val ACCUMULATION_MIN_CHANGE_PCT = -0.5  // >= this → ACCUMULATION
    private const val MAX_LOOKBACK_DAYS = 10

    data class Result(
        val signalType: String,
        val volumeRatio: Double,
        val priceChangePct: Double,
        val consecutiveDays: Int,
    )

    fun compute(series: BarSeries): Result? {
        if (series.barCount < MIN_BARS_REQUIRED) return null

        val endIdx = series.endIndex

        // 20-day avg volume: use bars before today to keep today's spike out of the baseline.
        val avgVol20d = (endIdx - 20 until endIdx)
            .sumOf { series.getBar(it).volume.doubleValue() } / 20.0

        if (avgVol20d <= 0) return null

        var consecutiveDays = 0
        var signalType: String? = null

        // Walk backward from today, counting consecutive days matching the same signal type.
        for (i in endIdx downTo maxOf(endIdx - MAX_LOOKBACK_DAYS, 1)) {
            val bar = series.getBar(i)
            val prevClose = series.getBar(i - 1).closePrice.doubleValue()
            val close = bar.closePrice.doubleValue()

            if (prevClose <= 0) break

            val volRatio = bar.volume.doubleValue() / avgVol20d
            val priceChangePct = (close - prevClose) / prevClose * 100.0

            // Both conditions must hold for this bar to count.
            if (volRatio < VOLUME_RATIO_THRESHOLD) break
            if (Math.abs(priceChangePct) > MAX_PRICE_MOVE_PCT) break

            val barType = if (priceChangePct >= ACCUMULATION_MIN_CHANGE_PCT) "ACCUMULATION" else "DISTRIBUTION"

            if (consecutiveDays == 0) {
                signalType = barType
            } else if (barType != signalType) {
                break  // signal type flipped mid-run — stop counting
            }

            consecutiveDays++
        }

        if (consecutiveDays < 2 || signalType == null) return null

        // Report figures from today's bar as the canonical signal values.
        val todayBar = series.getBar(endIdx)
        val prevClose = series.getBar(endIdx - 1).closePrice.doubleValue()
        val todayClose = todayBar.closePrice.doubleValue()
        val todayVolRatio = todayBar.volume.doubleValue() / avgVol20d
        val todayPriceChangePct = if (prevClose > 0) (todayClose - prevClose) / prevClose * 100.0 else 0.0

        return Result(
            signalType = signalType,
            volumeRatio = todayVolRatio,
            priceChangePct = todayPriceChangePct,
            consecutiveDays = consecutiveDays,
        )
    }
}
