package com.tradingtool.core.strategy.weeklybase

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.kite.InstrumentCache
import java.time.LocalDate

@Singleton
class WeeklyBaseDefinitionService @Inject constructor(
    private val candleCacheService: CandleCacheService,
    private val instrumentCache: InstrumentCache,
    private val engine: WeeklyBaseDefinitionEngine,
    private val configService: WeeklyBaseDefinitionConfigService,
) {
    suspend fun run(config: WeeklyBaseDefinitionRunConfig): WeeklyBaseDefinitionReport {
        val symbol = config.symbol.trim().uppercase()
        require(symbol.isNotBlank()) { "symbol is required." }
        val instrument = instrumentCache.find("NSE", symbol)
            ?.takeIf { candidate -> candidate.instrument_type == "EQ" }
            ?: throw IllegalArgumentException("Unknown NSE equity symbol: $symbol")
        return engine.run(symbol, loadCandles(symbol, instrument.instrument_token, config.toDate), configService.loadConfig())
    }

    private suspend fun loadCandles(symbol: String, instrumentToken: Long, toDate: LocalDate): List<DailyCandle> {
        val fromDate = toDate.minusDays(HISTORY_CALENDAR_DAYS)
        return candleCacheService.getDailyCandles(instrumentToken, symbol, fromDate, toDate)
            .sortedBy(DailyCandle::candleDate)
    }

    private companion object {
        const val HISTORY_CALENDAR_DAYS = 800L
    }
}
