package com.tradingtool.core.strategy.csvbacktest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CsvBacktestDeliveryMetricsTest {

    @Test
    fun `returns breakout delivery and maximum delivery from the five prior trading sessions`() {
        val signalDate = LocalDate.of(2026, 7, 10)
        val priorTradingDates = listOf(
            LocalDate.of(2026, 7, 2),
            LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 7, 6),
            LocalDate.of(2026, 7, 7),
            LocalDate.of(2026, 7, 8),
            LocalDate.of(2026, 7, 9),
        )
        val deliveryPctByDate = mapOf(
            LocalDate.of(2026, 7, 2) to 91.0,
            LocalDate.of(2026, 7, 3) to 48.0,
            LocalDate.of(2026, 7, 6) to 65.0,
            LocalDate.of(2026, 7, 7) to 73.0,
            LocalDate.of(2026, 7, 8) to 59.0,
            LocalDate.of(2026, 7, 9) to 68.0,
            signalDate to 62.0,
        )

        val metrics = calculateCsvBacktestDeliveryMetrics(signalDate, priorTradingDates, deliveryPctByDate)

        assertEquals(62.0, metrics.breakoutDayDeliveryPct)
        assertEquals(73.0, metrics.priorFiveDaysMaxDeliveryPct)
        assertEquals(
            listOf(
                CsvBacktestPriorDeliveryMetric(LocalDate.of(2026, 7, 3), 48.0),
                CsvBacktestPriorDeliveryMetric(LocalDate.of(2026, 7, 6), 65.0),
                CsvBacktestPriorDeliveryMetric(LocalDate.of(2026, 7, 7), 73.0),
                CsvBacktestPriorDeliveryMetric(LocalDate.of(2026, 7, 8), 59.0),
                CsvBacktestPriorDeliveryMetric(LocalDate.of(2026, 7, 9), 68.0),
            ),
            metrics.priorFiveDaysDelivery,
        )
    }
}
