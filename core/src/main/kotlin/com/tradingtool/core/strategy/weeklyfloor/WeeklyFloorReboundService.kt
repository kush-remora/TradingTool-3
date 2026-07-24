package com.tradingtool.core.strategy.weeklyfloor

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.screener.CandleDataService
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Singleton
class WeeklyFloorReboundService @Inject constructor(
    private val candleCacheService: CandleCacheService,
    private val candleDataService: CandleDataService,
    private val instrumentCache: InstrumentCache,
    private val kiteClient: KiteConnectClient,
    private val engine: WeeklyFloorReboundEngine,
) {
    suspend fun run(config: WeeklyFloorReboundRunConfig): WeeklyFloorReboundReport {
        val symbol = config.symbol.trim().uppercase()
        require(symbol.isNotBlank()) { "symbol is required." }
        val instrument = instrumentCache.find("NSE", symbol)
            ?.takeIf { candidate -> candidate.instrument_type == "EQ" }
            ?: throw IllegalArgumentException("Unknown NSE equity symbol: $symbol")
        val candles = loadCandles(symbol, instrument.instrument_token, config.toDate)
        return engine.run(symbol, candles, config.backtestTradingDays)
    }

    private suspend fun loadCandles(symbol: String, instrumentToken: Long, toDate: LocalDate): List<DailyCandle> {
        val fromDate = toDate.minusDays(HISTORY_CALENDAR_DAYS)
        var candles = candleCacheService.getDailyCandles(instrumentToken, symbol, fromDate, toDate)
            .sortedBy(DailyCandle::candleDate)
        val latestDate = candles.lastOrNull()?.candleDate
        val latestGapDays = latestDate?.let { date -> ChronoUnit.DAYS.between(date, toDate) } ?: Long.MAX_VALUE
        if (candles.isEmpty() || latestGapDays > MAX_ALLOWED_LATEST_GAP_DAYS) {
            candleDataService.syncDailyRange(listOf(symbol), fromDate, toDate, kiteClient)
            candles = candleCacheService.getDailyCandles(instrumentToken, symbol, fromDate, toDate)
                .sortedBy(DailyCandle::candleDate)
        }
        return candles
    }

    companion object {
        private const val HISTORY_CALENDAR_DAYS = 800L
        private const val MAX_ALLOWED_LATEST_GAP_DAYS = 3L
    }
}
