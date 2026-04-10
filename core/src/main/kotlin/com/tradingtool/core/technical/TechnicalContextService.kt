package com.tradingtool.core.technical

import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.database.StockJdbiHandler
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class TechnicalContextService(
    private val stockHandler: StockJdbiHandler,
    private val candleCache: CandleCacheService,
) {
    private val ist = ZoneId.of("Asia/Kolkata")

    suspend fun getContext(symbol: String): TechnicalContext? {
        val stock = stockHandler.read { it.getBySymbol(symbol, "NSE") } ?: return null
        val today = LocalDate.now(ist)
        val from = today.minusYears(5)

        val candles = candleCache.getDailyCandles(stock.instrumentToken, symbol, from, today)
        if (candles.isEmpty()) {
            return null
        }

        val series = candles.toTa4jSeries(stock.symbol)
        val rsiList = series.calculateRsiValues(period = 14, fallback = 50.0)

        val ltp = candles.last().close
        val atr14 = StrategyTechnicalSignals.latestAtr14(candles)
        val sma200 = if (series.barCount >= 200) series.calculateSma(200).getDoubleValue(series.endIndex) else 0.0

        val currentRsi = rsiList.lastOrNull() ?: 50.0
        val lowestRsi50d = rsiList.takeLast(50).minOrNull() ?: 50.0
        val highestRsi50d = rsiList.takeLast(50).maxOrNull() ?: 50.0
        val lowestRsi100d = rsiList.takeLast(100).minOrNull() ?: 50.0
        val highestRsi100d = rsiList.takeLast(100).maxOrNull() ?: 50.0
        val lowestRsi200d = rsiList.takeLast(200).minOrNull() ?: 50.0
        val highestRsi200d = rsiList.takeLast(200).maxOrNull() ?: 50.0

        val threeMonthBounds = StrategyTechnicalSignals
            .buildRollingRsiBoundsMap(candles)
            .let { boundsByDate -> boundsByDate[candles.last().candleDate] }
        val adaptiveRsi = AdaptiveRsi.getStatus(
            currentRsi = currentRsi,
            lowestRsi = threeMonthBounds?.lowest ?: lowestRsi100d,
            highestRsi = threeMonthBounds?.highest ?: highestRsi100d,
        )

        val recentSessions = candles.takeLast(10).map { candle ->
            val range = candle.high - candle.low
            val lowToHighPct = if (candle.low > 0.0) ((candle.high - candle.low) / candle.low) * 100.0 else 0.0
            SessionCandle(
                date = candle.candleDate.toString(),
                dayOfWeek = candle.candleDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                open = candle.open.roundTo2(),
                high = candle.high.roundTo2(),
                low = candle.low.roundTo2(),
                close = candle.close.roundTo2(),
                volume = candle.volume,
                range = range.roundTo2(),
                lowToHighPct = lowToHighPct.roundTo2(),
            )
        }.reversed()

        return TechnicalContext(
            symbol = symbol,
            atr14 = atr14.roundTo2(),
            rsi14 = currentRsi.roundTo2(),
            lowestRsi50d = lowestRsi50d.roundTo2(),
            highestRsi50d = highestRsi50d.roundTo2(),
            lowestRsi100d = lowestRsi100d.roundTo2(),
            highestRsi100d = highestRsi100d.roundTo2(),
            lowestRsi200d = lowestRsi200d.roundTo2(),
            highestRsi200d = highestRsi200d.roundTo2(),
            sma200 = sma200.roundTo2(),
            ltp = ltp.roundTo2(),
            recentSessions = recentSessions,
            adaptiveRsi = adaptiveRsi
        )
    }
}
