package com.tradingtool.core.strategy.phasedbreakout

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class PhaseCDeliveryValidationAnalyzerTest {
    @Test
    fun `marks stock passed when conviction counts clear both windows`() {
        val evaluationDate = LocalDate.of(2026, 6, 24)
        val candles = (0 until 60).map { offset ->
            val date = evaluationDate.minusDays((59 - offset).toLong())
            val close = if (offset < 6) 95.0 else 110.0 + offset
            candle(date = date, close = close)
        }
        val deliveries = candles.mapIndexed { index, candle ->
            val isConvictionDay = index >= 56
            deliveryRow(
                tradingDate = candle.candleDate,
                deliveryQuantity = if (isConvictionDay) 180L else 100L,
                deliveryPct = if (isConvictionDay) 60.0 else 48.0,
            )
        }

        val result = PhaseCDeliveryValidationAnalyzer.evaluate(
            evaluationDate = evaluationDate,
            deliveries = deliveries,
            candles = candles,
        )

        assertEquals("PASSED", result.status)
        assertEquals("strong_delivery_support", result.reason)
        assertEquals(4, result.deliverySpikeDays10d)
        assertEquals(4, result.deliverySpikeDays20d)
        assertEquals(4, result.deliverySupportDays10d)
        assertEquals(4, result.deliverySupportDays20d)
        assertEquals(100L, result.wholesaleBaseDq)
        assertEquals(1.8, result.deliverySpikeRatio)
    }

    @Test
    fun `marks stock data missing when history is too short`() {
        val evaluationDate = LocalDate.of(2026, 6, 24)
        val candles = (0 until 15).map { offset ->
            candle(
                date = evaluationDate.minusDays((14 - offset).toLong()),
                close = 100.0 + offset,
            )
        }
        val deliveries = candles.map { candle ->
            deliveryRow(
                tradingDate = candle.candleDate,
                deliveryQuantity = 100L,
                deliveryPct = 52.0,
            )
        }

        val result = PhaseCDeliveryValidationAnalyzer.evaluate(
            evaluationDate = evaluationDate,
            deliveries = deliveries,
            candles = candles,
        )

        assertEquals("DATA_MISSING", result.status)
        assertEquals("insufficient_delivery_history", result.reason)
        assertNull(result.wholesaleBaseDq)
    }

    @Test
    fun `marks stock not passed when conviction threshold never clears`() {
        val evaluationDate = LocalDate.of(2026, 6, 24)
        val candles = (0 until 60).map { offset ->
            val date = evaluationDate.minusDays((59 - offset).toLong())
            val close = if (offset < 6) 90.0 else 120.0 + offset
            candle(date = date, close = close)
        }
        val deliveries = candles.map { candle ->
            deliveryRow(
                tradingDate = candle.candleDate,
                deliveryQuantity = 110L,
                deliveryPct = 51.0,
            )
        }

        val result = PhaseCDeliveryValidationAnalyzer.evaluate(
            evaluationDate = evaluationDate,
            deliveries = deliveries,
            candles = candles,
        )

        assertEquals("NOT_PASSED", result.status)
        assertEquals("no_delivery_confirmation", result.reason)
        assertEquals(0, result.deliverySpikeDays10d)
        assertEquals(0, result.deliverySpikeDays20d)
        assertEquals(0, result.deliverySupportDays10d)
        assertEquals(0, result.deliverySupportDays20d)
        assertEquals(110L, result.wholesaleBaseDq)
        assertEquals(1.0, result.deliverySpikeRatio)
    }

    @Test
    fun `requires supportive delivery percent alongside delivery spike`() {
        val evaluationDate = LocalDate.of(2026, 6, 24)
        val candles = (0 until 60).map { offset ->
            val date = evaluationDate.minusDays((59 - offset).toLong())
            val close = if (offset < 6) 92.0 else 118.0 + offset
            candle(date = date, close = close)
        }
        val deliveries = candles.mapIndexed { index, candle ->
            val isLateSpike = index >= 56
            deliveryRow(
                tradingDate = candle.candleDate,
                deliveryQuantity = if (isLateSpike) 180L else 100L,
                deliveryPct = 50.0,
            )
        }

        val result = PhaseCDeliveryValidationAnalyzer.evaluate(
            evaluationDate = evaluationDate,
            deliveries = deliveries,
            candles = candles,
        )

        assertEquals("NOT_PASSED", result.status)
        assertEquals(4, result.deliverySpikeDays10d)
        assertEquals(4, result.deliverySpikeDays20d)
        assertEquals(0, result.deliverySupportDays10d)
        assertEquals(0, result.deliverySupportDays20d)
    }

    private fun deliveryRow(
        tradingDate: LocalDate,
        deliveryQuantity: Long,
        deliveryPct: Double,
    ): StockDeliveryDaily {
        return StockDeliveryDaily(
            instrumentToken = 101L,
            symbol = "TEST",
            exchange = "NSE",
            universe = "watchlist",
            tradingDate = tradingDate,
            reconciliationStatus = DeliveryReconciliationStatus.PRESENT,
            series = "EQ",
            ttlTrdQnty = 300L,
            delivQty = deliveryQuantity,
            delivPer = deliveryPct,
            sourceFileName = null,
            sourceUrl = null,
            fetchedAt = OffsetDateTime.parse("2026-06-24T12:00:00Z"),
        )
    }

    private fun candle(
        date: LocalDate,
        close: Double,
    ): DailyCandle {
        return DailyCandle(
            instrumentToken = 101L,
            symbol = "TEST",
            candleDate = date,
            open = close,
            high = close + 1.0,
            low = close - 1.0,
            close = close,
            volume = 1_000L,
        )
    }
}
