package com.tradingtool.core.strategy.netwebcycle

import com.google.inject.Inject
import com.google.inject.Singleton
import com.tradingtool.core.candle.CandleCacheService
import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolverService
import java.time.LocalDate

@Singleton
class NetwebCycleService @Inject constructor(
    private val candleCacheService: CandleCacheService,
    private val instrumentCache: InstrumentCache,
    private val instrumentTokenResolver: InstrumentTokenResolverService,
    private val engine: NetwebCycleEngine,
    private val configService: NetwebCycleConfigService,
) {
    suspend fun run(config: NetwebCycleRunConfig): NetwebCycleReport {
        val symbol = config.symbol.trim().uppercase()
        require(symbol.isNotBlank()) { "symbol is required." }
        val instrumentToken = instrumentTokenResolver.resolve("NSE", symbol)
            ?: throw IllegalArgumentException("Unknown NSE equity symbol: $symbol")
        val instrument = instrumentCache.find(instrumentToken)
            ?.takeIf { candidate -> candidate.instrument_type == "EQ" }
            ?: throw IllegalArgumentException("Unknown NSE equity symbol: $symbol")
        val candles = loadCandles(symbol, instrument.instrument_token, config.toDate)
        return engine.run(symbol, candles, configService.loadConfig())
    }

    private suspend fun loadCandles(symbol: String, instrumentToken: Long, toDate: LocalDate): List<DailyCandle> =
        candleCacheService.getDailyCandles(
            token = instrumentToken,
            symbol = symbol,
            from = toDate.minusDays(HISTORY_CALENDAR_DAYS),
            to = toDate,
        ).sortedBy(DailyCandle::candleDate)

    private companion object {
        const val HISTORY_CALENDAR_DAYS = 800L
    }
}
