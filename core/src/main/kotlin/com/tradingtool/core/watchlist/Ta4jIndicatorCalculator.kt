package com.tradingtool.core.watchlist

import com.tradingtool.core.model.watchlist.ComputedIndicators
import org.ta4j.core.BarSeries
import org.ta4j.core.indicators.EMAIndicator
import org.ta4j.core.indicators.MACDIndicator
import org.ta4j.core.indicators.ROCIndicator
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.HighestValueIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator

object Ta4jIndicatorCalculator {

    /**
     * Calculates the required technical indicators from a ta4j BarSeries.
     * The BarSeries should ideally contain 1 year (approx 252 bars) of daily data.
     */
    fun calculate(series: BarSeries): ComputedIndicators {
        if (series.barCount == 0) return ComputedIndicators(computedAt = System.currentTimeMillis())

        val closePrice = ClosePriceIndicator(series)
        val lastIndex = series.endIndex
        
        // SMA 50 & 200
        val sma50 = if (series.barCount >= 50) SMAIndicator(closePrice, 50).getValue(lastIndex).doubleValue() else null
        val sma200 = if (series.barCount >= 200) SMAIndicator(closePrice, 200).getValue(lastIndex).doubleValue() else null

        // RSI 14
        val rsi14 = if (series.barCount >= 14) RSIIndicator(closePrice, 14).getValue(lastIndex).doubleValue() else null

        // ROC 1W (5 days) & 3M (60 days)
        val roc1w = if (series.barCount >= 5) ROCIndicator(closePrice, 5).getValue(lastIndex).doubleValue() else null
        val roc3m = if (series.barCount >= 60) ROCIndicator(closePrice, 60).getValue(lastIndex).doubleValue() else null

        // MACD (12, 26) with Signal (9)
        var macdSignalStr: String? = null
        if (series.barCount >= 26) {
            val macd = MACDIndicator(closePrice, 12, 26)
            val macdVal = macd.getValue(lastIndex).doubleValue()
            val signalIndicator = EMAIndicator(macd, 9)
            val signalVal = signalIndicator.getValue(lastIndex).doubleValue()
            
            macdSignalStr = when {
                macdVal > signalVal -> "BULLISH"
                macdVal < signalVal -> "BEARISH"
                else -> "FLAT"
            }
        }

        // Drawdown % (from highest close in memory)
        val highest1y = HighestValueIndicator(closePrice, series.barCount).getValue(lastIndex).doubleValue()
        val lastClose = closePrice.getValue(lastIndex).doubleValue()
        val drawdownPct = if (highest1y > 0) ((lastClose - highest1y) / highest1y) * 100.0 else 0.0

        // Average Volume (20 days)
        val volume = VolumeIndicator(series)
        val avgVol20d = if (series.barCount >= 20) SMAIndicator(volume, 20).getValue(lastIndex).doubleValue() else null

        return ComputedIndicators(
            sma50 = sma50,
            sma200 = sma200,
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
            computedAt = System.currentTimeMillis()
        )
    }
}
