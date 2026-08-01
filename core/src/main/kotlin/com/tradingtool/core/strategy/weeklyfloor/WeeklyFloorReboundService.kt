package com.tradingtool.core.strategy.weeklyfloor

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.kite.InstrumentCache
import java.time.LocalDate

@Singleton
class WeeklyFloorReboundService @Inject constructor(
    private val candleCacheService: CandleCacheService,
    private val instrumentCache: InstrumentCache,
    private val engine: WeeklyFloorReboundEngine,
) {
    suspend fun run(config: WeeklyFloorReboundRunConfig): WeeklyFloorReboundReport {
        val symbol = config.symbol.trim().uppercase()
        require(symbol.isNotBlank()) { "symbol is required." }
        require(config.supportFloor > 0) { "supportFloor must be greater than zero." }
        require(config.supportCeiling >= config.supportFloor) { "supportCeiling must be at least supportFloor." }
        val instrument = instrumentCache.find("NSE", symbol)
            ?.takeIf { candidate -> candidate.instrument_type == "EQ" }
            ?: throw IllegalArgumentException("Unknown NSE equity symbol: $symbol")
        val candles = loadCandles(symbol, instrument.instrument_token, config.toDate)
        return engine.run(symbol, candles, config.supportFloor, config.supportCeiling, config.activeFrom)
    }

    private suspend fun loadCandles(symbol: String, instrumentToken: Long, toDate: LocalDate): List<DailyCandle> {
        val fromDate = toDate.minusDays(HISTORY_CALENDAR_DAYS)
        return candleCacheService.getDailyCandles(instrumentToken, symbol, fromDate, toDate)
            .sortedBy(DailyCandle::candleDate)
    }

    companion object {
        private const val HISTORY_CALENDAR_DAYS = 800L
    }
}
