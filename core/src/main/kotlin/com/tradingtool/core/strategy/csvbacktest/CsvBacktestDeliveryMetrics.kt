package com.tradingtool.core.strategy.csvbacktest

import java.time.LocalDate

internal data class CsvBacktestDeliveryMetrics(
    val breakoutDayDeliveryPct: Double?,
    val priorFiveDaysMaxDeliveryPct: Double?,
)

internal fun calculateCsvBacktestDeliveryMetrics(
    signalDate: LocalDate,
    priorTradingDates: List<LocalDate>,
    deliveryPctByDate: Map<LocalDate, Double?>,
): CsvBacktestDeliveryMetrics = CsvBacktestDeliveryMetrics(
    breakoutDayDeliveryPct = deliveryPctByDate[signalDate],
    priorFiveDaysMaxDeliveryPct = priorTradingDates
        .takeLast(5)
        .mapNotNull { date -> deliveryPctByDate[date] }
        .maxOrNull(),
)
