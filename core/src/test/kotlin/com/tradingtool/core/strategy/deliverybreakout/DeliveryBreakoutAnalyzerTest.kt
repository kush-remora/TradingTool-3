package com.tradingtool.core.strategy.deliverybreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class DeliveryBreakoutAnalyzerTest {
    private val config = DeliveryBreakoutConfig()

    @Test
    fun `stage1 candidate requires both volume and delivery to beat prior 10 day maxima`() {
        val tradeDate = LocalDate.of(2026, 6, 23)
        val history = (1..10).map { offset ->
            deliveryRow(
                tradingDate = tradeDate.minusDays((11 - offset).toLong()),
                volume = 10_000L + offset,
                deliveryQuantity = 5_000L + offset,
            )
        }
        val current = deliveryRow(
            tradingDate = tradeDate,
            volume = 20_000L,
            deliveryQuantity = 8_000L,
        )

        val candidate = requireNotNull(
            DeliveryBreakoutAnalyzer.buildStage1Candidate(
                row = current,
                history = history,
                config = config,
            ),
        )

        assertEquals(20_000L, candidate.volume)
        assertEquals(10_010L, candidate.prior10dMaxVolume)
        assertEquals(5_010L, candidate.prior10dMaxDeliveryQuantity)
        assertEquals(2.0, candidate.volumeRatioVs10dMax)
        assertEquals(1.6, candidate.deliveryRatioVs10dMax)
    }

    @Test
    fun `stage1 candidate rejects rows with insufficient history`() {
        val tradeDate = LocalDate.of(2026, 6, 23)
        val history = (1..9).map { offset ->
            deliveryRow(
                tradingDate = tradeDate.minusDays(offset.toLong()),
                volume = 10_000L + offset,
                deliveryQuantity = 5_000L + offset,
            )
        }

        val candidate = DeliveryBreakoutAnalyzer.buildStage1Candidate(
            row = deliveryRow(tradingDate = tradeDate, volume = 20_000L, deliveryQuantity = 9_000L),
            history = history,
            config = config,
        )

        assertNull(candidate)
    }

    @Test
    fun `quiet clue resolves to d minus 1 before d minus 2`() {
        val tradeDate = LocalDate.of(2026, 6, 23)
        val deliveries = (0..12).map { offset ->
            val day = tradeDate.minusDays((12 - offset).toLong())
            val isQuietClueDay = day == tradeDate.minusDays(1) || day == tradeDate.minusDays(2)
            deliveryRow(
                tradingDate = day,
                volume = if (isQuietClueDay) 25_000L + offset else 10_000L + offset,
                deliveryQuantity = if (isQuietClueDay) 12_000L + offset else 5_000L + offset,
            )
        }
        val candles = listOf(
            candle(tradeDate.minusDays(3), close = 100.0),
            candle(tradeDate.minusDays(2), close = 98.0),
            candle(tradeDate.minusDays(1), close = 99.0),
            candle(tradeDate, close = 103.0),
        )

        val quietClueDay = DeliveryBreakoutAnalyzer.resolveQuietClueDay(
            deliveries = deliveries,
            candles = candles,
            tradeDate = tradeDate,
            config = config,
        )

        assertEquals(tradeDate.minusDays(1), quietClueDay)
    }

    @Test
    fun `quiet clue uses previous trading sessions instead of calendar days`() {
        val tradeDate = LocalDate.of(2026, 6, 22)
        val tradingDates = listOf(
            LocalDate.of(2026, 6, 5),
            LocalDate.of(2026, 6, 8),
            LocalDate.of(2026, 6, 9),
            LocalDate.of(2026, 6, 10),
            LocalDate.of(2026, 6, 11),
            LocalDate.of(2026, 6, 12),
            LocalDate.of(2026, 6, 15),
            LocalDate.of(2026, 6, 16),
            LocalDate.of(2026, 6, 17),
            LocalDate.of(2026, 6, 18),
            LocalDate.of(2026, 6, 19),
            LocalDate.of(2026, 6, 22),
        )
        val deliveries = tradingDates.mapIndexed { index, date ->
            deliveryRow(
                tradingDate = date,
                volume = if (date == LocalDate.of(2026, 6, 19)) 30_000L else 10_000L + index,
                deliveryQuantity = if (date == LocalDate.of(2026, 6, 19)) 15_000L else 5_000L + index,
            )
        }
        val candles = listOf(
            candle(LocalDate.of(2026, 6, 18), close = 100.0),
            candle(LocalDate.of(2026, 6, 19), close = 101.0),
            candle(LocalDate.of(2026, 6, 22), close = 104.0),
        )

        val quietClueDay = DeliveryBreakoutAnalyzer.resolveQuietClueDay(
            deliveries = deliveries,
            candles = candles,
            tradeDate = tradeDate,
            config = config,
        )

        assertEquals(LocalDate.of(2026, 6, 19), quietClueDay)
    }

    @Test
    fun `confirmed breakout requires greater than six percent move`() {
        assertFalse(DeliveryBreakoutAnalyzer.isConfirmedBreakoutToday(6.0))
        assertTrue(DeliveryBreakoutAnalyzer.isConfirmedBreakoutToday(6.01))
        assertFalse(DeliveryBreakoutAnalyzer.isConfirmedBreakoutToday(null))
    }

    @Test
    fun `sma proximity allows any lower bound but caps two percent above`() {
        assertEquals(true, DeliveryBreakoutAnalyzer.isNearSma200(-15.0))
        assertEquals(true, DeliveryBreakoutAnalyzer.isNearSma200(2.0))
        assertEquals(false, DeliveryBreakoutAnalyzer.isNearSma200(2.01))
        assertNull(DeliveryBreakoutAnalyzer.isNearSma200(null))
    }

    private fun deliveryRow(
        tradingDate: LocalDate,
        volume: Long,
        deliveryQuantity: Long,
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

    private fun candle(date: LocalDate, close: Double): DailyCandle {
        return DailyCandle(
            instrumentToken = 101L,
            symbol = "TEST",
            candleDate = date,
            open = close,
            high = close + 1.0,
            low = close - 1.0,
            close = close,
            volume = 20_000L,
        )
    }
}
