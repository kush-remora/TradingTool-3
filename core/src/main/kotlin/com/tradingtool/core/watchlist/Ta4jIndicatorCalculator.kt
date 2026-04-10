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
import com.tradingtool.core.technical.calculateSma
import com.tradingtool.core.technical.getNullableDouble

object Ta4jIndicatorCalculator {

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

        // 2-month range based on last 40 closes
        val high40d = if (series.barCount >= 40) HighestValueIndicator(closePrice, 40).getNullableDouble(lastIndex) else null
        val low40d = if (series.barCount >= 40) LowestValueIndicator(closePrice, 40).getNullableDouble(lastIndex) else null

        // RSI 14
        val rsi14 = if (series.barCount >= 14) series.calculateRsi(14).getNullableDouble(lastIndex) else null

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

        // Average Volume (20 days)
        val volume = VolumeIndicator(series)
        val avgVol20d = if (series.barCount >= 20) SMAIndicator(volume, 20).getNullableDouble(lastIndex) else null

        return ComputedIndicators(
            sma50 = sma50,
            sma200 = sma200,
            high40d = high40d,
            low40d = low40d,
            rsi14 = rsi14,
            roc1w = roc1w,
            roc3m = roc3m,
            macdSignal = macdSignalStr,
            drawdownPct = drawdownPct,
            // maxDd1y requires a true peak-to-trough walk (different from drawdownPct which is
            // only current price vs period high). Left null until properly implemented rather
            // than silently returning a wrong value.
            maxDd1y = null,
            avgVol20d = avgVol20d,
            computedAt = System.currentTimeMillis(),
        )
    }
}
