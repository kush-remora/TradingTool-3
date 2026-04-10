package com.tradingtool.core.watchlist

import com.tradingtool.core.model.watchlist.ComputedIndicators
import com.tradingtool.core.technical.calculateRsi
import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.EMAIndicator
import org.ta4j.core.indicators.MACDIndicator
import org.ta4j.core.indicators.ROCIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.HighestValueIndicator
import org.ta4j.core.indicators.helpers.LowestValueIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import com.tradingtool.core.technical.calculateAtr
import com.tradingtool.core.technical.calculateSma
import com.tradingtool.core.technical.getNullableDouble

object Ta4jIndicatorCalculator {
    private const val RANGE_WINDOW_60D = 60
    private const val RANGE_WINDOW_3M = 63
    private const val PRICE_EPSILON = 1e-8

    /**
     * Calculates the required technical indicators from a ta4j BarSeries.
     *
     * While standard indicators like RSI/EMA benefit from the 5-year warmup provided
     * by the Service layer for mathematical convergence, specific windowed metrics
     * (like Drawdown) are constrained to the last 252 bars (1 trading year).
     */
    fun calculate(series: BarSeries): ComputedIndicators {
        if (series.barCount == 0) return ComputedIndicators(computedAt = System.currentTimeMillis())

        val closePrice = ClosePriceIndicator(series)
        val lastIndex = series.endIndex

        // SMA 50 & 200
        val sma50 = if (series.barCount >= 50) series.calculateSma(50).getNullableDouble(lastIndex) else null
        val sma200 = if (series.barCount >= 200) series.calculateSma(200).getNullableDouble(lastIndex) else null

        // RSI 14
        val rsi14Indicator = if (series.barCount >= 14) series.calculateRsi(14) else null
        val rsi14 = rsi14Indicator?.getNullableDouble(lastIndex)
        val atr14 = if (series.barCount >= 14) series.calculateAtr(14).getNullableDouble(lastIndex) else null

        // ROC 1W (5 days) & 3M (60 days)
        val roc1w = if (series.barCount >= 5) ROCIndicator(closePrice, 5).getNullableDouble(lastIndex) else null
        val roc3m = if (series.barCount >= 60) ROCIndicator(closePrice, 60).getNullableDouble(lastIndex) else null

        // MACD (12, 26) with Signal (9)
        val macdSignalStr = if (series.barCount >= 26) {
            val macd = MACDIndicator(closePrice, 12, 26)
            val signalIndicator = EMAIndicator(macd, 9)
            val macdVal = macd.getNullableDouble(lastIndex)
            val signalVal = signalIndicator.getNullableDouble(lastIndex)

            when {
                macdVal == null || signalVal == null -> null
                macdVal > signalVal -> "BULLISH"
                macdVal < signalVal -> "BEARISH"
                else -> "FLAT"
            }
        } else {
            null
        }

        // Drawdown % (from highest close in the last 1 year / 252 bars)
        val highest1y = HighestValueIndicator(closePrice, minOf(252, series.barCount)).getNullableDouble(lastIndex)
        val lastClose = closePrice.getNullableDouble(lastIndex)
        val drawdownPct = if (highest1y != null && highest1y > 0.0 && lastClose != null) {
            ((lastClose - highest1y) / highest1y) * 100.0
        } else {
            0.0
        }

        val volume = VolumeIndicator(series)

        // 2-month context range (60 bars): high/low and RSI/volume at those extremes.
        val high60d = if (series.barCount >= RANGE_WINDOW_60D) {
            HighestValueIndicator(closePrice, RANGE_WINDOW_60D).getNullableDouble(lastIndex)
        } else {
            null
        }
        val low60d = if (series.barCount >= RANGE_WINDOW_60D) {
            LowestValueIndicator(closePrice, RANGE_WINDOW_60D).getNullableDouble(lastIndex)
        } else {
            null
        }
        val high3m = if (series.barCount >= RANGE_WINDOW_3M) {
            HighestValueIndicator(closePrice, RANGE_WINDOW_3M).getNullableDouble(lastIndex)
        } else {
            null
        }
        val low3m = if (series.barCount >= RANGE_WINDOW_3M) {
            LowestValueIndicator(closePrice, RANGE_WINDOW_3M).getNullableDouble(lastIndex)
        } else {
            null
        }

        var bestHighIndex: Int? = null
        var bestLowIndex: Int? = null
        var rsiAtHigh60d: Double? = null
        var rsiAtLow60d: Double? = null

        if (series.barCount >= RANGE_WINDOW_60D && high60d != null && low60d != null && rsi14Indicator != null) {
            val startIndex = lastIndex - (RANGE_WINDOW_60D - 1)
            for (index in startIndex..lastIndex) {
                val close = closePrice.getNullableDouble(index) ?: continue
                val rsi = rsi14Indicator.getNullableDouble(index) ?: continue

                if (kotlin.math.abs(close - high60d) <= PRICE_EPSILON) {
                    val currentBestRsi = rsiAtHigh60d
                    val currentBestIndex = bestHighIndex
                    val shouldReplace = currentBestRsi == null ||
                        rsi > currentBestRsi ||
                        (rsi == currentBestRsi && (currentBestIndex == null || index > currentBestIndex))
                    if (shouldReplace) {
                        rsiAtHigh60d = rsi
                        bestHighIndex = index
                    }
                }

                if (kotlin.math.abs(close - low60d) <= PRICE_EPSILON) {
                    val currentBestRsi = rsiAtLow60d
                    val currentBestIndex = bestLowIndex
                    val shouldReplace = currentBestRsi == null ||
                        rsi < currentBestRsi ||
                        (rsi == currentBestRsi && (currentBestIndex == null || index > currentBestIndex))
                    if (shouldReplace) {
                        rsiAtLow60d = rsi
                        bestLowIndex = index
                    }
                }
            }
        }

        val volumeAtHigh60d = bestHighIndex?.let { volume.getNullableDouble(it) }
        val volumeAtLow60d = bestLowIndex?.let { volume.getNullableDouble(it) }

        // Average Volume (20 days)
        val avgVol20d = if (series.barCount >= 20) SMAIndicator(volume, 20).getNullableDouble(lastIndex) else null
        val lastVolume = volume.getNullableDouble(lastIndex)
        val volumeVsAvg20d = if (lastVolume != null && avgVol20d != null && avgVol20d != 0.0) {
            lastVolume / avgVol20d
        } else {
            null
        }

        return ComputedIndicators(
            lastClose = lastClose,
            sma50 = sma50,
            sma200 = sma200,
            high60d = high60d,
            low60d = low60d,
            high3m = high3m,
            low3m = low3m,
            rsiAtHigh60d = rsiAtHigh60d,
            rsiAtLow60d = rsiAtLow60d,
            volumeAtHigh60d = volumeAtHigh60d,
            volumeAtLow60d = volumeAtLow60d,
            rsi14 = rsi14,
            atr14 = atr14,
            roc1w = roc1w,
            roc3m = roc3m,
            macdSignal = macdSignalStr,
            drawdownPct = drawdownPct,
            // maxDd1y requires a true peak-to-trough walk (different from drawdownPct which is
            // only current price vs period high). Left null until properly implemented rather
            // than silently returning a wrong value.
            maxDd1y = null,
            avgVol20d = avgVol20d,
            volumeVsAvg20d = volumeVsAvg20d,
            computedAt = System.currentTimeMillis(),
        )
    }
}
