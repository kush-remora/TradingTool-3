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
    private val config = DeliveryBreakoutConfig(volumeMultiplier = 2.0, deliveryMultiplier = 2.0)

    @Test
    fun `stage1 candidate requires both volume and delivery to beat prior day by multiplier`() {
        val tradeDate = LocalDate.of(2026, 6, 23)
        val history = listOf(
            deliveryRow(
                tradingDate = tradeDate.minusDays(1),
                volume = 10_000L,
                deliveryQuantity = 5_000L,
            )
        )
        val current = deliveryRow(
            tradingDate = tradeDate,
            volume = 20_000L,
            deliveryQuantity = 10_000L,
        )

        val candidate = requireNotNull(
            DeliveryBreakoutAnalyzer.buildStage1Candidate(
                row = current,
                history = history,
                config = config,
            ),
        )

        assertEquals(20_000L, candidate.volume)
        assertEquals(10_000L, candidate.prevVolume)
        assertEquals(5_000L, candidate.prevDeliveryQuantity)
        assertEquals(2.0, candidate.volumeRatio)
        assertEquals(2.0, candidate.deliveryRatio)
    }

    @Test
    fun `stage1 candidate rejects if volume or delivery fails to beat multiplier`() {
        val tradeDate = LocalDate.of(2026, 6, 23)
        val history = listOf(
            deliveryRow(
                tradingDate = tradeDate.minusDays(1),
                volume = 10_000L,
                deliveryQuantity = 5_000L,
            )
        )
        
        // Volume high enough, delivery too low
        assertNull(
            DeliveryBreakoutAnalyzer.buildStage1Candidate(
                row = deliveryRow(tradingDate = tradeDate, volume = 20_000L, deliveryQuantity = 9_000L),
                history = history,
                config = config,
            )
        )
        
        // Delivery high enough, volume too low
        assertNull(
            DeliveryBreakoutAnalyzer.buildStage1Candidate(
                row = deliveryRow(tradingDate = tradeDate, volume = 19_000L, deliveryQuantity = 10_000L),
                history = history,
                config = config,
            )
        )
    }

    @Test
    fun `stage1 candidate rejects rows with no history`() {
        val tradeDate = LocalDate.of(2026, 6, 23)
        val candidate = DeliveryBreakoutAnalyzer.buildStage1Candidate(
            row = deliveryRow(tradingDate = tradeDate, volume = 20_000L, deliveryQuantity = 9_000L),
            history = emptyList(),
            config = config,
        )

        assertNull(candidate)
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
