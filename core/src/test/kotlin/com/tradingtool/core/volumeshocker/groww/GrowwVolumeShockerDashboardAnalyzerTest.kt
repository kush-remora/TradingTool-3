package com.tradingtool.core.volumeshocker.groww

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.LocalDate

class GrowwVolumeShockerDashboardAnalyzerTest {

    @Test
    fun `counts appearances and streak using trading dates only`() {
        val lookbackDescending = listOf(
            LocalDate.parse("2026-06-10"),
            LocalDate.parse("2026-06-09"),
            LocalDate.parse("2026-06-08"),
            LocalDate.parse("2026-06-05"),
        )
        val appearedDates = setOf(
            LocalDate.parse("2026-06-10"),
            LocalDate.parse("2026-06-09"),
            LocalDate.parse("2026-06-05"),
        )

        assertEquals(3, GrowwVolumeShockerDashboardAnalyzer.calculateAppearanceCount(lookbackDescending.asReversed(), appearedDates))
        assertEquals(2, GrowwVolumeShockerDashboardAnalyzer.calculateStreakLength(lookbackDescending, appearedDates))
    }

    @Test
    fun `flags pre-event accumulation when delivery jumps on a quiet day`() {
        val eventDate = LocalDate.parse("2026-06-10")
        val candles = listOf(
            candle("2026-05-27", 100.0, 100.0),
            candle("2026-05-28", 100.0, 100.5),
            candle("2026-05-29", 100.5, 100.2),
            candle("2026-06-01", 100.2, 100.0),
            candle("2026-06-02", 100.0, 99.8),
            candle("2026-06-03", 99.8, 100.1),
            candle("2026-06-04", 100.1, 100.0),
            candle("2026-06-05", 100.0, 99.5),
            candle("2026-06-08", 99.5, 99.4),
            candle("2026-06-09", 99.4, 99.3),
            candle("2026-06-10", 99.3, 114.0),
        )
        val deliveries = listOf(
            delivery("2026-05-27", 400, 40.0),
            delivery("2026-05-28", 450, 40.0),
            delivery("2026-05-29", 500, 40.0),
            delivery("2026-06-01", 550, 40.0),
            delivery("2026-06-02", 600, 40.0),
            delivery("2026-06-03", 650, 40.0),
            delivery("2026-06-04", 700, 40.0),
            delivery("2026-06-05", 750, 40.0),
            delivery("2026-06-08", 800, 40.0),
            delivery("2026-06-09", 2_000, 40.0),
            delivery("2026-06-10", 2_500, 42.0),
        )

        val metrics = GrowwVolumeShockerDashboardAnalyzer.calculatePreEventMetrics(candles, deliveries, eventDate)

        assertEquals(2_000, metrics.maxDeliveryVolume)
        assertTrue(metrics.hasAccumulationHint)
    }

    @Test
    fun `does not flag accumulation when delivery spike happens on a large price move`() {
        val eventDate = LocalDate.parse("2026-06-10")
        val candles = listOf(
            candle("2026-06-01", 100.0, 100.0),
            candle("2026-06-02", 100.0, 100.0),
            candle("2026-06-03", 100.0, 100.0),
            candle("2026-06-04", 100.0, 100.0),
            candle("2026-06-05", 100.0, 100.0),
            candle("2026-06-08", 100.0, 110.0),
            candle("2026-06-09", 110.0, 111.0),
            candle("2026-06-10", 111.0, 112.0),
        )
        val deliveries = listOf(
            delivery("2026-06-01", 500, 40.0),
            delivery("2026-06-02", 500, 40.0),
            delivery("2026-06-03", 500, 40.0),
            delivery("2026-06-04", 500, 40.0),
            delivery("2026-06-05", 500, 40.0),
            delivery("2026-06-08", 2_000, 40.0),
            delivery("2026-06-09", 800, 40.0),
            delivery("2026-06-10", 900, 40.0),
        )

        val metrics = GrowwVolumeShockerDashboardAnalyzer.calculatePreEventMetrics(candles, deliveries, eventDate)

        assertFalse(metrics.hasAccumulationHint)
    }

    @Test
    fun `builds detail window with event day highlighted and skips days without delivery`() {
        val eventDate = LocalDate.parse("2026-06-10")
        val candles = listOf(
            candle("2026-06-03", 100.0, 100.0),
            candle("2026-06-04", 100.0, 101.0),
            candle("2026-06-05", 101.0, 102.0),
            candle("2026-06-06", 102.0, 103.0),
            candle("2026-06-07", 103.0, 104.0),
            candle("2026-06-08", 104.0, 105.0),
            candle("2026-06-09", 105.0, 106.0),
            candle("2026-06-10", 106.0, 107.0),
            candle("2026-06-11", 107.0, 108.0),
        )
        val deliveries = listOf(
            delivery("2026-06-03", 100, 40.0),
            delivery("2026-06-04", 100, 40.0),
            delivery("2026-06-05", 100, 40.0),
            delivery("2026-06-08", 100, 40.0),
            delivery("2026-06-09", 100, 40.0),
            delivery("2026-06-10", 100, 40.0),
            delivery("2026-06-11", 100, 40.0),
        )

        val detailDays = GrowwVolumeShockerDashboardAnalyzer.buildDetailWindow(candles, deliveries, eventDate)

        assertEquals(listOf("2026-06-05", "2026-06-08", "2026-06-09", "2026-06-10", "2026-06-11"), detailDays.map { day -> day.date })
        assertTrue(detailDays.first { day -> day.date == "2026-06-10" }.is_event_day)
        assertFalse(detailDays.any { day -> day.date == "2026-06-06" || day.date == "2026-06-07" })
    }

    private fun candle(date: String, open: Double, close: Double): DailyCandle {
        return DailyCandle(
            instrumentToken = 101L,
            symbol = "ABC",
            candleDate = LocalDate.parse(date),
            open = open,
            high = maxOf(open, close),
            low = minOf(open, close),
            close = close,
            volume = 1_000L,
        )
    }

    private fun delivery(date: String, deliveryQty: Long, deliveryPct: Double): StockDeliveryDaily {
        return StockDeliveryDaily(
            instrumentToken = 101L,
            symbol = "ABC",
            exchange = "NSE",
            universe = "TEST",
            tradingDate = LocalDate.parse(date),
            reconciliationStatus = DeliveryReconciliationStatus.PRESENT,
            series = "EQ",
            ttlTrdQnty = deliveryQty * 2,
            delivQty = deliveryQty,
            delivPer = deliveryPct,
            sourceFileName = null,
            sourceUrl = null,
            fetchedAt = null,
        )
    }
}
