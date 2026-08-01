package com.tradingtool.core.screener

import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.zerodhatech.models.Instrument
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CandleDataServiceTest {
    @Test
    fun `current Kite token replaces a stale stored token`() = runBlocking {
        val currentToken = 408_065L
        val instrumentCache = InstrumentCache().apply {
            refresh(listOf(instrument(symbol = "INFY", token = currentToken)))
        }
        val kiteClient = KiteConnectClient(KiteConfig(apiKey = "test", apiSecret = "test"))
        val service = CandleDataService(
            instrumentCache = instrumentCache,
            tokenResolver = InstrumentTokenResolverService(kiteClient, instrumentCache),
            kiteClient = kiteClient,
        )

        val resolvedToken = service.resolveInstrumentToken(symbol = "INFY", token = 999L)

        assertEquals(currentToken, resolvedToken)
    }

    private fun instrument(symbol: String, token: Long): Instrument = Instrument().apply {
        exchange = "NSE"
        tradingsymbol = symbol
        instrument_token = token
    }
}
