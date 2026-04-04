package com.tradingtool.core.strategy.remora

import com.tradingtool.core.technical.getNullableDouble
import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import java.time.LocalDate

/**
 * Detects Remora (institutional accumulation/distribution) signals from a daily BarSeries.
 *
 * Signal fires when:
 *   - Volume >= 1.5x the 20-day average (computed from the 20 bars before today)
 *   - Price barely moved: abs(dailyChangePct) <= 1.5%
 *   - Delivery % >= 35%
 *   - Delivery Ratio (today_pct / avg_20d_prior) >= 1.20
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
    private const val DELIVERY_PCT_THRESHOLD = 35.0
    private const val DELIVERY_RATIO_THRESHOLD = 1.20
    private const val MAX_LOOKBACK_DAYS = 10

    data class Result(
        val signalType: String,
        val volumeRatio: Double,
        val priceChangePct: Double,
        val consecutiveDays: Int,
        val deliveryPct: Double,
        val deliveryRatio: Double,
        val tradingDate: LocalDate,
    )

    data class DeliveryMetrics(
        val deliveryPct: Double,
        val deliveryRatio: Double
    )

    fun compute(series: BarSeries, deliveryMap: Map<LocalDate, DeliveryMetrics>): Result? {
        if (series.barCount < MIN_BARS_REQUIRED) return null

        val endIdx = series.endIndex
        
        // Use ta4j SMAIndicator for 20-day avg volume.
        // Evaluation at endIdx - 1 ensures today's spike is excluded from the baseline.
        val volumeIndicator = VolumeIndicator(series)
        val sma20Volume = SMAIndicator(volumeIndicator, 20)
        val avgVol20d = sma20Volume.getNullableDouble(endIdx - 1) ?: return null

        if (avgVol20d <= 0.0) return null

        var consecutiveDays = 0
        var signalType: String? = null

        // Walk backward from today, counting consecutive days matching the same signal type.
        for (i in endIdx downTo maxOf(endIdx - MAX_LOOKBACK_DAYS, 1)) {
            val bar = series.getBar(i)
            val tradingDate = bar.endTime.toLocalDate()
            val prevClose = series.getBar(i - 1).closePrice.doubleValue()
            val close = bar.closePrice.doubleValue()

            if (prevClose <= 0) break

            val volRatio = bar.volume.doubleValue() / avgVol20d
            val priceChangePct = (close - prevClose) / prevClose * 100.0
            
            // Delivery gates
            val delivery = deliveryMap[tradingDate]

            // Both conditions must hold for this bar to count.
            if (volRatio < VOLUME_RATIO_THRESHOLD) break
            if (Math.abs(priceChangePct) > MAX_PRICE_MOVE_PCT) break
            
            // New Delivery Gates
            if (delivery == null || delivery.deliveryPct < DELIVERY_PCT_THRESHOLD || delivery.deliveryRatio < DELIVERY_RATIO_THRESHOLD) break

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
        val todayDate = todayBar.endTime.toLocalDate()
        val prevClose = series.getBar(endIdx - 1).closePrice.doubleValue()
        val todayClose = todayBar.closePrice.doubleValue()
        val todayVolRatio = todayBar.volume.doubleValue() / avgVol20d
        val todayPriceChangePct = if (prevClose > 0) (todayClose - prevClose) / prevClose * 100.0 else 0.0
        val todayDelivery = deliveryMap[todayDate]!! // must exist if loop passed

        return Result(
            signalType = signalType,
            volumeRatio = todayVolRatio,
            priceChangePct = todayPriceChangePct,
            consecutiveDays = consecutiveDays,
            deliveryPct = todayDelivery.deliveryPct,
            deliveryRatio = todayDelivery.deliveryRatio,
            tradingDate = todayDate
        )
    }
}
