package com.tradingtool.core.strategy.deliverybreakout

import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class DeliveryBreakoutAnalyzerTest {
    private val tradeDate = LocalDate.of(2026, 6, 23)
    private val evaluationDates = listOf(tradeDate)

    @Test
    fun `detects both volume and delivery shocks`() {
        val events = DeliveryBreakoutAnalyzer.buildEvents(
            symbol = "TEST",
            instrumentToken = 101L,
            history = baselineRows() + deliveryRow(tradeDate, 200L, 100L),
            evaluationDates = evaluationDates,
            baselineSessions = 10,
            shockMultiplier = 2.0,
        )

        assertEquals(1, events.size)
        assertEquals("BOTH", events.single().eventType)
        assertEquals(2.0, events.single().volumeRatio)
        assertEquals(2.0, events.single().deliveryRatio)
    }

    @Test
    fun `detects volume-only and delivery-only shocks`() {
        val volumeOnly = DeliveryBreakoutAnalyzer.buildEvents(
            symbol = "TEST",
            instrumentToken = 101L,
            history = baselineRows() + deliveryRow(tradeDate, 200L, 50L),
            evaluationDates = evaluationDates,
            baselineSessions = 10,
            shockMultiplier = 2.0,
        )
        val deliveryOnly = DeliveryBreakoutAnalyzer.buildEvents(
            symbol = "TEST",
            instrumentToken = 101L,
            history = baselineRows() + deliveryRow(tradeDate, 100L, 100L),
            evaluationDates = evaluationDates,
            baselineSessions = 10,
            shockMultiplier = 2.0,
        )

        assertEquals("VOLUME_ONLY", volumeOnly.single().eventType)
        assertEquals("DELIVERY_ONLY", deliveryOnly.single().eventType)
    }

    @Test
    fun `does not emit a non-shock day`() {
        val events = DeliveryBreakoutAnalyzer.buildEvents(
            symbol = "TEST",
            instrumentToken = 101L,
            history = baselineRows() + deliveryRow(tradeDate, 150L, 75L),
            evaluationDates = evaluationDates,
            baselineSessions = 10,
            shockMultiplier = 2.0,
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `uses exactly the preceding ten sessions and ignores future rows`() {
        val events = DeliveryBreakoutAnalyzer.buildEvents(
            symbol = "TEST",
            instrumentToken = 101L,
            history = baselineRows() +
                deliveryRow(tradeDate, 150L, 75L) +
                deliveryRow(tradeDate.plusDays(1), 10_000L, 10_000L),
            evaluationDates = evaluationDates,
            baselineSessions = 10,
            shockMultiplier = 2.0,
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun `emits a volume event when delivery data is missing`() {
        val events = DeliveryBreakoutAnalyzer.buildEvents(
            symbol = "TEST",
            instrumentToken = 101L,
            history = baselineRows() + deliveryRow(tradeDate, 200L, null),
            evaluationDates = evaluationDates,
            baselineSessions = 10,
            shockMultiplier = 2.0,
        )

        assertEquals("VOLUME_ONLY", events.single().eventType)
        assertEquals(null, events.single().deliveryQuantity)
    }

    @Test
    fun `emits a delivery event when volume data is missing`() {
        val events = DeliveryBreakoutAnalyzer.buildEvents(
            symbol = "TEST",
            instrumentToken = 101L,
            history = baselineRows() + deliveryRow(tradeDate, null, 100L),
            evaluationDates = evaluationDates,
            baselineSessions = 10,
            shockMultiplier = 2.0,
        )

        assertEquals("DELIVERY_ONLY", events.single().eventType)
        assertEquals(null, events.single().volume)
    }

    @Test
    fun `marks accumulation candidate when ten of twenty prior sessions are quiet inside a ten percent range`() {
        val candidate = DeliveryBreakoutAnalyzer.calculatePreEventAccumulationMetrics(
            candles = accumulationCandles(quietDays = 10, endingClose = 105.0),
            eventDate = tradeDate,
        )
        val nonCandidate = DeliveryBreakoutAnalyzer.calculatePreEventAccumulationMetrics(
            candles = accumulationCandles(quietDays = 9, endingClose = 105.0),
            eventDate = tradeDate,
        )

        assertEquals(10, candidate.quietDays20d)
        assertTrue(candidate.range20dPct != null && candidate.range20dPct <= 10.0)
        assertTrue(candidate.preEventChange10dPct != null && candidate.preEventChange10dPct > 0.0 && candidate.preEventChange10dPct < 10.0)
        assertTrue(candidate.preEventUpward10d)
        assertTrue(candidate.isCandidate20d)
        assertEquals(9, nonCandidate.quietDays20d)
        assertFalse(nonCandidate.isCandidate20d)
    }

    private fun baselineRows(): List<StockDeliveryDaily> {
        return (10 downTo 1).map { offset ->
            deliveryRow(tradeDate.minusDays(offset.toLong()), 100L, 50L)
        }
    }

    private fun deliveryRow(
        tradingDate: LocalDate,
        volume: Long?,
        deliveryQuantity: Long?,
    ): StockDeliveryDaily {
        return StockDeliveryDaily(
            instrumentToken = 101L,
            symbol = "TEST",
            exchange = "NSE",
            universe = "watchlist",
            tradingDate = tradingDate,
            reconciliationStatus = DeliveryReconciliationStatus.PRESENT,
            series = "EQ",
            ttlTrdQnty = volume,
            delivQty = deliveryQuantity,
            delivPer = 55.0,
            sourceFileName = null,
            sourceUrl = null,
            fetchedAt = OffsetDateTime.parse("2026-06-23T12:00:00Z"),
        )
    }

    private fun accumulationCandles(quietDays: Int, endingClose: Double): List<DailyCandle> {
        val priorCandles = (1..20).map { session ->
            val quiet = session <= quietDays
            val close = 100.0 + ((endingClose - 100.0) * session / 20.0)
            DailyCandle(
                instrumentToken = 101L,
                symbol = "TEST",
                candleDate = tradeDate.minusDays((21 - session).toLong()),
                open = 100.0,
                high = if (quiet) 101.0 else 104.0,
                low = if (quiet) 99.5 else 100.0,
                close = close,
                volume = 1_000L,
            )
        }
        return priorCandles + DailyCandle(
            instrumentToken = 101L,
            symbol = "TEST",
            candleDate = tradeDate,
            open = 100.0,
            high = 120.0,
            low = 90.0,
            close = 110.0,
            volume = 2_000L,
        )
    }
}
