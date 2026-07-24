package com.tradingtool.core.strategy.absolutedelivery

import com.tradingtool.core.delivery.model.StockDeliveryDaily
import java.time.LocalDate

data class AbsoluteDeliveryCriteria(
    val minimumTradedQuantityInclusive: Long = 20_000_000L,
    val minimumDeliveryQuantityExclusive: Long = 10_000_000L,
    val minimumDeliveryPercentageExclusive: Double = 60.0,
)

data class AbsoluteDeliveryBacktestSummary(
    val universeKey: String,
    val fromDate: String,
    val toDate: String,
    val watchlistSymbolCount: Int,
    val tradingDateCount: Int,
    val expectedRowCount: Int,
    val evaluatedRowCount: Int,
    val missingRowCount: Int,
    val matchedRowCount: Int,
)

enum class AbsoluteDeliveryDataStatus {
    AVAILABLE,
    MISSING_FROM_SOURCE,
    INCOMPLETE,
    NO_RECORD,
}

data class AbsoluteDeliveryBacktestRow(
    val symbol: String,
    val companyName: String,
    val tradingDate: String,
    val tradedQuantity: Long?,
    val deliveryQuantity: Long?,
    val deliveryPercentage: Double?,
    val tradedQuantityPassed: Boolean,
    val deliveryQuantityPassed: Boolean,
    val deliveryPercentagePassed: Boolean,
    val matched: Boolean,
    val dataStatus: AbsoluteDeliveryDataStatus,
)

data class AbsoluteDeliveryBacktestResponse(
    val criteria: AbsoluteDeliveryCriteria,
    val summary: AbsoluteDeliveryBacktestSummary,
    val matchedRows: List<AbsoluteDeliveryBacktestRow>,
    val allRows: List<AbsoluteDeliveryBacktestRow>,
)

internal data class AbsoluteDeliveryWatchlistMember(
    val symbol: String,
    val companyName: String,
    val instrumentToken: Long,
)

internal data class AbsoluteDeliveryBacktestInput(
    val universeKey: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val members: List<AbsoluteDeliveryWatchlistMember>,
    val tradingDates: List<LocalDate>,
    val deliveries: List<StockDeliveryDaily>,
    val criteria: AbsoluteDeliveryCriteria = AbsoluteDeliveryCriteria(),
)
