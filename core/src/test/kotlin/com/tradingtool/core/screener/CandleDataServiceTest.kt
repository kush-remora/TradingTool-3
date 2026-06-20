package com.tradingtool.core.screener

import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.candle.dao.CandleWriteDao
import com.tradingtool.core.database.JdbiHandler
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.DatabaseConfig
import com.tradingtool.core.stock.dao.StockReadDao
import com.tradingtool.core.stock.dao.StockWriteDao
import com.zerodhatech.models.Instrument
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CandleDataServiceTest {

    @Test
    fun `resolveInstrumentToken falls back to BE symbol through shared resolver`() = runBlocking {
        val cache = populatedCache(
            instrument(exchange = "NSE", symbol = "STLTECH-BE", token = 2384129L),
        )
        val service = createService(cache)

        val token = service.resolveInstrumentToken(symbol = "STLTECH", stockInstrumentToken = null)

        assertEquals(2384129L, token)
    }

    @Test
    fun `resolveInstrumentToken prefers stored stock token when present`() = runBlocking {
        val cache = populatedCache(
            instrument(exchange = "NSE", symbol = "STLTECH-BE", token = 2384129L),
        )
        val service = createService(cache)

        val token = service.resolveInstrumentToken(symbol = "STLTECH", stockInstrumentToken = 99L)

        assertEquals(99L, token)
    }

    private fun createService(cache: InstrumentCache): CandleDataService {
        val kiteClient = KiteConnectClient(KiteConfig(apiKey = "test", apiSecret = "test"))
        return CandleDataService(
            stockHandler = emptyStockHandler(),
            candleHandler = emptyCandleHandler(),
            instrumentCache = cache,
            tokenResolver = InstrumentTokenResolverService(kiteClient, cache),
        )
    }

    private fun populatedCache(vararg instruments: Instrument): InstrumentCache {
        val cache = InstrumentCache()
        cache.refresh(instruments.toList())
        return cache
    }

    private fun emptyStockHandler(): JdbiHandler<StockReadDao, StockWriteDao> =
        JdbiHandler(
            config = DatabaseConfig(jdbcUrl = ""),
            readDaoClass = StockReadDao::class.java,
            writeDaoClass = StockWriteDao::class.java,
        )

    private fun emptyCandleHandler(): JdbiHandler<CandleReadDao, CandleWriteDao> =
        JdbiHandler(
            config = DatabaseConfig(jdbcUrl = ""),
            readDaoClass = CandleReadDao::class.java,
            writeDaoClass = CandleWriteDao::class.java,
        )

    private fun instrument(exchange: String, symbol: String, token: Long): Instrument =
        Instrument().apply {
            this.exchange = exchange
            this.tradingsymbol = symbol
            this.instrument_token = token
        }
}
