package com.tradingtool.core.analysis.swing

import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.StockJdbiHandler
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class SwingService(
    private val stockHandler: StockJdbiHandler,
    private val candleCache: CandleCacheService,
) {
    private val ist = ZoneId.of("Asia/Kolkata")

    suspend fun analyzeSwings(
        symbol: String,
        reversalPct: Double,
        lookbackDays: Int = 365
    ): SwingAnalysisResponse? {
        val stock = stockHandler.read { it.getBySymbol(symbol, "NSE") } ?: return null
        val today = LocalDate.now(ist)
        val from = today.minusDays(lookbackDays.toLong())

        val candles = candleCache.getDailyCandles(stock.instrumentToken, symbol, from, today)
        if (candles.size < 2) return null

        val points = mutableListOf<SwingPoint>()
        
        // Initial setup
        var lastPointPrice = candles[0].close
        var currentHigh = candles[0].high
        var currentLow = candles[0].low
        var highIdx = 0
        var lowIdx = 0
        
        // Determine initial trend
        var trend = 0 // 0 = unknown, 1 = up, -1 = down
        
        for (i in 1 until candles.size) {
            val candle = candles[i]
            
            if (trend == 0) {
                if (candle.high > currentHigh) {
                    currentHigh = candle.high
                    highIdx = i
                }
                if (candle.low < currentLow) {
                    currentLow = candle.low
                    lowIdx = i
                }
                
                if (currentHigh > currentLow * (1 + reversalPct / 100.0)) {
                    trend = 1
                    // Mark initial trough
                    points.add(createPoint(candles[lowIdx], lastPointPrice, SwingType.TROUGH, 0))
                    lastPointPrice = currentLow
                } else if (currentLow < currentHigh * (1 - reversalPct / 100.0)) {
                    trend = -1
                    // Mark initial peak
                    points.add(createPoint(candles[highIdx], lastPointPrice, SwingType.PEAK, 0))
                    lastPointPrice = currentHigh
                }
            } else if (trend == 1) { // Up trend
                if (candle.high > currentHigh) {
                    currentHigh = candle.high
                    highIdx = i
                }
                
                // Check for reversal
                if (candle.low < currentHigh * (1 - reversalPct / 100.0)) {
                    // Reversal confirmed, mark peak
                    val peakCandle = candles[highIdx]
                    val change = ((peakCandle.high - lastPointPrice) / lastPointPrice) * 100.0
                    val barsSince = highIdx - (points.lastOrNull()?.let { p -> candles.indexOfFirst { it.candleDate.toString() == p.date } } ?: 0)
                    
                    points.add(createPoint(peakCandle, lastPointPrice, SwingType.PEAK, barsSince, true))
                    
                    trend = -1
                    currentLow = candle.low
                    lowIdx = i
                    lastPointPrice = peakCandle.high
                }
            } else if (trend == -1) { // Down trend
                if (candle.low < currentLow) {
                    currentLow = candle.low
                    lowIdx = i
                }
                
                // Check for reversal
                if (candle.high > currentLow * (1 + reversalPct / 100.0)) {
                    // Reversal confirmed, mark trough
                    val troughCandle = candles[lowIdx]
                    val change = ((troughCandle.low - lastPointPrice) / lastPointPrice) * 100.0
                    val barsSince = lowIdx - (points.lastOrNull()?.let { p -> candles.indexOfFirst { it.candleDate.toString() == p.date } } ?: 0)
                    
                    points.add(createPoint(troughCandle, lastPointPrice, SwingType.TROUGH, barsSince, false))
                    
                    trend = 1
                    currentHigh = candle.high
                    highIdx = i
                    lastPointPrice = troughCandle.low
                }
            }
        }

        // Calculate stats
        val upswings = points.filter { it.type == SwingType.PEAK }.map { it.changePct }
        val downswings = points.filter { it.type == SwingType.TROUGH }.map { it.changePct }
        val durations = points.map { it.barsSinceLast.toDouble() }.filter { it > 0 }

        val stats = SwingStats(
            averageUpswingPct = if (upswings.isNotEmpty()) upswings.average() else 0.0,
            averageDownswingPct = if (downswings.isNotEmpty()) downswings.average() else 0.0,
            averageSwingDurationBars = if (durations.isNotEmpty()) durations.average() else 0.0
        )

        return SwingAnalysisResponse(
            symbol = symbol,
            reversalPct = reversalPct,
            points = points,
            candles = candles.map { 
                SwingCandle(
                    date = it.candleDate.toString(),
                    open = it.open,
                    high = it.high,
                    low = it.low,
                    close = it.close,
                    volume = it.volume
                )
            },
            stats = stats
        )
    }

    private fun createPoint(
        candle: com.tradingtool.core.candle.DailyCandle,
        prevPrice: Double,
        type: SwingType,
        barsSince: Int,
        isHigh: Boolean = true
    ): SwingPoint {
        val price = if (isHigh) candle.high else candle.low
        val change = if (prevPrice != 0.0) ((price - prevPrice) / prevPrice) * 100.0 else 0.0
        
        return SwingPoint(
            date = candle.candleDate.toString(),
            dayOfWeek = candle.candleDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            price = price.roundTo2(),
            type = type,
            changePct = change.roundTo2(),
            barsSinceLast = barsSince
        )
    }

    private fun Double.roundTo2(): Double =
        Math.round(this * 100.0) / 100.0
}
