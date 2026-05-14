package com.tradingtool.core.watchlist.groww

import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.zerodhatech.models.Instrument
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KiteInstrumentTokenResolverTest {

    @Test
    fun `resolve returns exact NSE match when available`() = runBlocking {
        val cache = populatedCache(
            instrument("NSE", "BHAGYANGR", 5318657L),
        )
        val resolver = KiteInstrumentTokenResolver(kiteClient = KiteConnectClient(KiteConfig(apiKey = "test", apiSecret = "test")), instrumentCache = cache)

        val token = resolver.resolve(exchange = "NSE", symbol = "BHAGYANGR")

        assertEquals(5318657L, token)
    }

    @Test
    fun `resolve falls back to BE symbol on NSE`() = runBlocking {
        val cache = populatedCache(
            instrument("NSE", "SCHNEIDER-BE", 7996929L),
        )
        val resolver = KiteInstrumentTokenResolver(kiteClient = KiteConnectClient(KiteConfig(apiKey = "test", apiSecret = "test")), instrumentCache = cache)

        val token = resolver.resolve(exchange = "NSE", symbol = "SCHNEIDER")

        assertEquals(7996929L, token)
    }

    @Test
    fun `resolve returns null when regex fallback has multiple NSE variants`() = runBlocking {
        val cache = populatedCache(
            instrument("NSE", "ABC-BL", 1L),
            instrument("NSE", "ABC-EQ", 2L),
        )
        val resolver = KiteInstrumentTokenResolver(kiteClient = KiteConnectClient(KiteConfig(apiKey = "test", apiSecret = "test")), instrumentCache = cache)

        val token = resolver.resolve(exchange = "NSE", symbol = "ABC")

        assertNull(token)
    }

    private fun populatedCache(vararg instruments: Instrument): InstrumentCache {
        val cache = InstrumentCache()
        cache.refresh(instruments.toList())
        return cache
    }

    private fun instrument(exchange: String, symbol: String, token: Long): Instrument =
        Instrument().apply {
            this.exchange = exchange
            this.tradingsymbol = symbol
            this.instrument_token = token
        }

    
}
