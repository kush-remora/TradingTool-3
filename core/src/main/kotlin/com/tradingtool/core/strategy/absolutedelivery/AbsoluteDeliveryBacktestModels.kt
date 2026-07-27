package com.tradingtool.core.strategy.absolutedelivery

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.indexconstituents.dao.IndexSummary
import java.time.LocalDate

data class AbsoluteDeliveryGroupingOption(
    val value: String,
    val count: Int,
)

data class AbsoluteDeliveryCriteria(
    val minimumTradedQuantityInclusive: Long = 20_000_000L,
    val minimumDeliveryQuantityExclusive: Long = 5_000_000L,
    val minimumDeliveryPercentageExclusive: Double = 60.0,
    val shortSmaPeriod: Int = 50,
    val longSmaPeriod: Int = 200,
    val shortSmaSlopeLookbackSessions: Int = 20,
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

enum class AbsoluteDeliveryTrendDataStatus {
    AVAILABLE,
    NO_CANDLE,
    INSUFFICIENT_HISTORY,
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
    val closePrice: Double?,
    val sma50: Double?,
    val sma200: Double?,
    val sma50TwentySessionsAgo: Double?,
    val priceAboveSma50Passed: Boolean,
    val sma50AboveSma200Passed: Boolean,
    val sma50RisingPassed: Boolean,
    val uptrendMatched: Boolean,
    val trendDataStatus: AbsoluteDeliveryTrendDataStatus,
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
    val candles: List<DailyCandle>,
    val criteria: AbsoluteDeliveryCriteria = AbsoluteDeliveryCriteria(),
)

internal fun absoluteDeliveryGroupingOptions(
    summaries: List<IndexSummary>,
): List<AbsoluteDeliveryGroupingOption> =
    summaries
        .map { summary ->
            AbsoluteDeliveryGroupingOption(
                value = summary.indexKey,
                count = summary.count,
            )
        }
        .sortedBy { option -> option.value.lowercase() }

internal fun resolveAbsoluteDeliveryGrouping(
    requestedGrouping: String?,
    summaries: List<IndexSummary>,
    defaultGrouping: String,
): String {
    val requested = requestedGrouping?.trim().takeUnless { value -> value.isNullOrEmpty() }
        ?: defaultGrouping
    return summaries
        .firstOrNull { summary -> summary.indexKey.equals(requested, ignoreCase = true) }
        ?.indexKey
        ?: throw IllegalArgumentException("Unknown institutional grouping: $requested")
}
