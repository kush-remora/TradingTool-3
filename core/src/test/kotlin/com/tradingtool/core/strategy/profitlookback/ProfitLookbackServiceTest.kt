package com.tradingtool.core.strategy.profitlookback

import com.tradingtool.core.kite.KiteConfig
import com.tradingtool.core.kite.KiteConnectClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class ProfitLookbackServiceTest {
    private val service = ProfitLookbackService(
        kiteClient = KiteConnectClient(KiteConfig(apiKey = "test-api-key", apiSecret = "test-api-secret"))
    )

    @Test
    fun `returns latest buy date that achieves target`() {
        val response = service.analyzeWithCandles(
            symbol = "INFY",
            instrumentToken = 123L,
            requestedSellDate = LocalDate.parse("2026-04-20"),
            lookbackDays = 10,
            targetPercents = listOf(10.0),
            candles = listOf(
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-10"), 80.0, 79.0),
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-14"), 89.0, 85.0),
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-17"), 93.0, 84.0),
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-20"), 100.0, 96.0),
            ),
        )

        val result = response.results.single()
        assertEquals("ACHIEVED", result.status)
        assertEquals("2026-04-14", result.suggestedBuyDate)
        assertEquals(6, result.daysBefore)
        assertEquals(12.3596, result.returnPercent)
        assertEquals(-5.618, result.maxDrawdownPercent)
        assertEquals(3, result.maxDrawdownDays)
    }

    @Test
    fun `marks target as not achievable when no buy date matches`() {
        val response = service.analyzeWithCandles(
            symbol = "INFY",
            instrumentToken = 123L,
            requestedSellDate = LocalDate.parse("2026-04-20"),
            lookbackDays = 10,
            targetPercents = listOf(30.0),
            candles = listOf(
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-11"), 90.0, 89.0),
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-15"), 95.0, 94.0),
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-20"), 100.0, 98.0),
            ),
        )

        val result = response.results.single()
        assertEquals("NOT_ACHIEVABLE", result.status)
        assertNull(result.suggestedBuyDate)
        assertNull(result.buyOpenPrice)
        assertNull(result.daysBefore)
        assertNull(result.returnPercent)
        assertNull(result.maxDrawdownPercent)
        assertNull(result.maxDrawdownDays)
    }

    @Test
    fun `resolves weekend sell date to previous trading day`() {
        val response = service.analyzeWithCandles(
            symbol = "INFY",
            instrumentToken = 123L,
            requestedSellDate = LocalDate.parse("2026-04-19"),
            lookbackDays = 10,
            targetPercents = listOf(2.0),
            candles = listOf(
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-16"), 97.0, 95.0),
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-17"), 100.0, 99.0),
            ),
        )

        assertEquals("2026-04-19", response.requestedSellDate)
        assertEquals("2026-04-17", response.resolvedSellDate)
        assertEquals(100.0, response.sellOpenPrice)
    }

    @Test
    fun `days before is calendar day difference`() {
        val response = service.analyzeWithCandles(
            symbol = "INFY",
            instrumentToken = 123L,
            requestedSellDate = LocalDate.parse("2026-04-20"),
            lookbackDays = 10,
            targetPercents = listOf(5.0),
            candles = listOf(
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-15"), 95.0, 93.0),
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-20"), 100.0, 99.0),
            ),
        )

        val result = response.results.single()
        assertEquals("2026-04-15", result.suggestedBuyDate)
        assertEquals(5, result.daysBefore)
    }

    @Test
    fun `drawdown uses lowest low between buy and sell dates`() {
        val response = service.analyzeWithCandles(
            symbol = "INFY",
            instrumentToken = 123L,
            requestedSellDate = LocalDate.parse("2026-04-20"),
            lookbackDays = 10,
            targetPercents = listOf(5.0),
            candles = listOf(
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-14"), 92.0, 90.0),
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-15"), 95.0, 92.0),
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-16"), 96.0, 80.0),
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-18"), 97.0, 94.0),
                ProfitLookbackDailyCandle(LocalDate.parse("2026-04-20"), 100.0, 96.0),
            ),
        )

        val result = response.results.single()
        assertEquals("2026-04-15", result.suggestedBuyDate)
        assertEquals(-15.7895, result.maxDrawdownPercent)
        assertEquals(1, result.maxDrawdownDays)
    }

    @Test
    fun `bulk analysis returns partial success and preserves row order`() {
        val response = service.analyzeBulkWithExecutor(
            request = ProfitLookbackBulkRequest(
                lookbackDays = 120,
                targetPercents = listOf(5.0, 10.0),
                rows = listOf(
                    ProfitLookbackBulkRowRequest("row-1", "INFY", 408065L, "2026-04-20"),
                    ProfitLookbackBulkRowRequest("row-2", "RELIANCE", 738561L, "2026-04-20"),
                ),
            ),
            analyzer = { request ->
                if (request.symbol == "RELIANCE") {
                    throw IllegalArgumentException("No daily candle found")
                }
                ProfitLookbackResponse(
                    symbol = request.symbol,
                    instrumentToken = request.instrumentToken,
                    requestedSellDate = request.sellDate,
                    resolvedSellDate = request.sellDate,
                    sellOpenPrice = 100.0,
                    results = emptyList(),
                )
            },
        )

        assertEquals(2, response.rows.size)
        assertEquals("row-1", response.rows[0].rowId)
        assertEquals(true, response.rows[0].ok)
        assertEquals("row-2", response.rows[1].rowId)
        assertEquals(false, response.rows[1].ok)
        assertEquals("No daily candle found", response.rows[1].error)
    }

    @Test
    fun `bulk analysis deduplicates repeated symbol token date requests`() {
        val callCount = AtomicInteger(0)
        val response = service.analyzeBulkWithExecutor(
            request = ProfitLookbackBulkRequest(
                lookbackDays = 120,
                targetPercents = listOf(5.0),
                rows = listOf(
                    ProfitLookbackBulkRowRequest("row-1", "INFY", 408065L, "2026-04-20"),
                    ProfitLookbackBulkRowRequest("row-2", "INFY", 408065L, "2026-04-20"),
                ),
            ),
            analyzer = { request ->
                callCount.incrementAndGet()
                ProfitLookbackResponse(
                    symbol = request.symbol,
                    instrumentToken = request.instrumentToken,
                    requestedSellDate = request.sellDate,
                    resolvedSellDate = request.sellDate,
                    sellOpenPrice = 100.0,
                    results = emptyList(),
                )
            },
        )

        assertEquals(1, callCount.get())
        assertEquals(2, response.rows.size)
        assertEquals(true, response.rows[0].ok)
        assertEquals(true, response.rows[1].ok)
    }
}
