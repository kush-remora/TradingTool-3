package com.tradingtool.core.technical

import com.tradingtool.core.candle.DailyCandle
import com.zerodhatech.models.HistoricalData
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.ATRIndicator
import org.ta4j.core.indicators.EMAIndicator
import org.ta4j.core.indicators.MACDIndicator
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.SMAIndicator
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator
import org.ta4j.core.indicators.bollinger.BollingerBandWidthIndicator
import org.ta4j.core.indicators.bollinger.PercentBIndicator
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.num.Num
import java.time.ZoneId

/**
 * Universal bindings bridging TradingTool models to native `org.ta4j` components.
 */

fun List<DailyCandle>.toTa4jSeries(name: String = "TradingToolSeries"): BarSeries {
    val series = BaseBarSeriesBuilder().withName(name).build()
    this.forEach { c ->
        series.addBar(c.candleDate.atStartOfDay(ZoneId.of("Asia/Kolkata")), c.open, c.high, c.low, c.close, c.volume)
    }
    return series
}

fun List<HistoricalData>.toTa4jSeriesFromKite(
    symbol: String = "TradingToolSeries",
    zone: ZoneId = ZoneId.of("Asia/Kolkata"),
): BarSeries {
    val series = BaseBarSeriesBuilder().withName(symbol).build()
    for (hk in this) {
        val localDt = java.time.LocalDateTime.parse(hk.timeStamp.substring(0, 19))
        val zdt: java.time.ZonedDateTime = localDt.atZone(zone)
        series.addBar(zdt, hk.open, hk.high, hk.low, hk.close, hk.volume)
    }
    return series
}

fun BarSeries.calculateRsi(period: Int = 14): RSIIndicator {
    return RSIIndicator(ClosePriceIndicator(this), period)
}

fun BarSeries.calculateSma(period: Int): SMAIndicator {
    return SMAIndicator(ClosePriceIndicator(this), period)
}

fun BarSeries.calculateEma(period: Int): EMAIndicator {
    return EMAIndicator(ClosePriceIndicator(this), period)
}

fun BarSeries.calculateAtr(period: Int = 14): ATRIndicator {
    return ATRIndicator(this, period)
}

fun BarSeries.calculateMacd(shortBar: Int = 12, longBar: Int = 26): MACDIndicator {
    return MACDIndicator(ClosePriceIndicator(this), shortBar, longBar)
}

fun BarSeries.calculateBollingerBands(period: Int = 20, k: Double = 2.0): Triple<BollingerBandsUpperIndicator, BollingerBandsMiddleIndicator, BollingerBandsLowerIndicator> {
    val closePrice = ClosePriceIndicator(this)
    val sma = SMAIndicator(closePrice, period)
    val sd = StandardDeviationIndicator(closePrice, period)
    val middle = BollingerBandsMiddleIndicator(sma)
    val upper = BollingerBandsUpperIndicator(middle, sd, this.numOf(k))
    val lower = BollingerBandsLowerIndicator(middle, sd, this.numOf(k))
    return Triple(upper, middle, lower)
}

fun BarSeries.calculateBollingerBandWidth(period: Int = 20, k: Double = 2.0): BollingerBandWidthIndicator {
    val (upper, middle, lower) = calculateBollingerBands(period, k)
    return BollingerBandWidthIndicator(upper, middle, lower)
}

fun BarSeries.calculateBollingerPercentB(period: Int = 20, k: Double = 2.0): PercentBIndicator {
    return PercentBIndicator(ClosePriceIndicator(this), period, k)
}


fun BarSeries.calculateRsiValues(period: Int = 14, fallback: Double = 50.0): List<Double> {
    if (barCount == 0) return emptyList()
    val rsi = calculateRsi(period)
    return (0..endIndex).map { index -> rsi.getDoubleValue(index, fallback) }
}

fun List<DailyCandle>.calculateRsiValues(period: Int = 14, fallback: Double = 50.0): List<Double> {
    if (isEmpty()) return emptyList()
    return toTa4jSeries().calculateRsiValues(period, fallback)
}

/**
 * Extension helper to safely resolve Double metric values,
 * treating initial unpadded bounds or edge boundaries as `0.0` or standard fallback logic.
 */
fun Indicator<Num>.getDoubleValue(index: Int, fallback: Double = 0.0): Double {
    if (index < 0) return fallback
    val raw = this.getValue(index).doubleValue()
    return if (raw.isFinite()) raw else fallback
}

fun Indicator<Num>.getNullableDouble(index: Int): Double? {
    if (index < 0) return null
    val raw = this.getValue(index).doubleValue()
    return if (raw.isFinite()) raw else null
}

fun Double.roundTo2(): Double = Math.round(this * 100.0) / 100.0
