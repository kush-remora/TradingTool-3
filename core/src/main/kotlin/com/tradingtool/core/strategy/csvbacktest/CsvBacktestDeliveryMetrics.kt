package com.tradingtool.core.strategy.csvbacktest

import java.time.LocalDate

internal data class CsvBacktestDeliveryMetrics(
    val breakoutDayDeliveryPct: Double?,
    val priorFiveDaysMaxDeliveryPct: Double?,
    val priorFiveDaysDelivery: List<CsvBacktestPriorDeliveryMetric>,
)

internal data class CsvBacktestPriorDeliveryMetric(
    val date: LocalDate,
    val deliveryPct: Double?,
)

internal fun calculateCsvBacktestDeliveryMetrics(
    signalDate: LocalDate,
    priorTradingDates: List<LocalDate>,
    deliveryPctByDate: Map<LocalDate, Double?>,
): CsvBacktestDeliveryMetrics {
    val priorFiveDaysDelivery = priorTradingDates
        .takeLast(5)
        .map { date -> CsvBacktestPriorDeliveryMetric(date, deliveryPctByDate[date]) }

    return CsvBacktestDeliveryMetrics(
        breakoutDayDeliveryPct = deliveryPctByDate[signalDate],
        priorFiveDaysMaxDeliveryPct = priorFiveDaysDelivery.mapNotNull(CsvBacktestPriorDeliveryMetric::deliveryPct).maxOrNull(),
        priorFiveDaysDelivery = priorFiveDaysDelivery,
    )
}
