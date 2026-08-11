package com.tradingtool.core.screener

import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.InstrumentTokenResolverService
import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import com.zerodhatech.models.Instrument
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `daily Kite requests are split into ranges below the broker limit`() {
        val ranges = dailyCandleRequestRanges(
            fromDate = LocalDate.of(2020, 1, 1),
            toDate = LocalDate.of(2026, 8, 10),
        )

        assertEquals(LocalDate.of(2020, 1, 1), ranges.first().from)
        assertEquals(LocalDate.of(2026, 8, 10), ranges.last().to)
        assertEquals(0, ranges.zipWithNext().count { (current, next) -> current.to.plusDays(1) != next.from })
        assertTrue(ranges.all { range -> range.from.plusDays(MAX_DAILY_REQUEST_DAYS - 1) >= range.to })
    }

    @Test
    fun `daily request ends at the next midnight for completed days and now for today`() {
        val now = LocalDateTime.of(2026, 8, 10, 12, 7, 34)

        assertEquals(
            LocalDateTime.of(2026, 8, 10, 0, 0),
            dailyCandleRequestEnd(LocalDate.of(2026, 8, 9), now),
        )
        assertEquals(now, dailyCandleRequestEnd(LocalDate.of(2026, 8, 10), now))
    }

    private fun instrument(symbol: String, token: Long): Instrument = Instrument().apply {
        exchange = "NSE"
        tradingsymbol = symbol
        instrument_token = token
    }
}
