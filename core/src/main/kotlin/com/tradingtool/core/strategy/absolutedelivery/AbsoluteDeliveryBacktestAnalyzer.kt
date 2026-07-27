package com.tradingtool.core.strategy.absolutedelivery

import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily

internal object AbsoluteDeliveryBacktestAnalyzer {

    fun buildResponse(input: AbsoluteDeliveryBacktestInput): AbsoluteDeliveryBacktestResponse {
        val deliveriesByTokenAndDate = input.deliveries.associateBy { delivery ->
            delivery.instrumentToken to delivery.tradingDate
        }
        val members = input.members.distinctBy { member -> member.symbol }
        val tradingDates = input.tradingDates.distinct()
        val trendContexts = buildAbsoluteDeliveryTrendContexts(
            candles = input.candles,
            criteria = input.criteria,
        )
        val allRows = tradingDates
            .flatMap { tradingDate ->
                members.map { member ->
                    buildRow(
                        member = member,
                        tradingDate = tradingDate,
                        delivery = deliveriesByTokenAndDate[member.instrumentToken to tradingDate],
                        trendContext = trendContexts[member.instrumentToken],
                        criteria = input.criteria,
                    )
                }
            }
            .sortedWith(
                compareByDescending<AbsoluteDeliveryBacktestRow> { row -> row.tradingDate }
                    .thenBy { row -> row.symbol },
            )
        val matchedRows = allRows
            .filter { row -> row.matched }
            .sortedWith(
                compareByDescending<AbsoluteDeliveryBacktestRow> { row -> row.tradingDate }
                    .thenByDescending { row -> row.deliveryQuantity }
                    .thenBy { row -> row.symbol },
            )

        return AbsoluteDeliveryBacktestResponse(
            criteria = input.criteria,
            summary = AbsoluteDeliveryBacktestSummary(
                universeKey = input.universeKey,
                fromDate = input.fromDate.toString(),
                toDate = input.toDate.toString(),
                watchlistSymbolCount = members.size,
                tradingDateCount = tradingDates.size,
                expectedRowCount = allRows.size,
                evaluatedRowCount = allRows.count { row ->
                    row.dataStatus == AbsoluteDeliveryDataStatus.AVAILABLE &&
                        row.trendDataStatus == AbsoluteDeliveryTrendDataStatus.AVAILABLE
                },
                missingRowCount = allRows.count { row ->
                    row.dataStatus != AbsoluteDeliveryDataStatus.AVAILABLE ||
                        row.trendDataStatus != AbsoluteDeliveryTrendDataStatus.AVAILABLE
                },
                matchedRowCount = matchedRows.size,
            ),
            matchedRows = matchedRows,
            allRows = allRows,
        )
    }

    private fun buildRow(
        member: AbsoluteDeliveryWatchlistMember,
        tradingDate: java.time.LocalDate,
        delivery: StockDeliveryDaily?,
        trendContext: AbsoluteDeliveryTrendContext?,
        criteria: AbsoluteDeliveryCriteria,
    ): AbsoluteDeliveryBacktestRow {
        val dataStatus = resolveDataStatus(delivery)
        val isAvailable = dataStatus == AbsoluteDeliveryDataStatus.AVAILABLE
        val tradedQuantity = delivery?.ttlTrdQnty
        val deliveryQuantity = delivery?.delivQty
        val deliveryPercentage = delivery?.delivPer
        val tradedQuantityPassed = isAvailable &&
            tradedQuantity != null &&
            tradedQuantity >= criteria.minimumTradedQuantityInclusive
        val deliveryQuantityPassed = isAvailable &&
            deliveryQuantity != null &&
            deliveryQuantity > criteria.minimumDeliveryQuantityExclusive
        val deliveryPercentagePassed = isAvailable &&
            deliveryPercentage != null &&
            deliveryPercentage > criteria.minimumDeliveryPercentageExclusive
        val trend = evaluateAbsoluteDeliveryTrend(
            context = trendContext,
            tradingDate = tradingDate,
            criteria = criteria,
        )

        return AbsoluteDeliveryBacktestRow(
            symbol = member.symbol,
            companyName = member.companyName,
            tradingDate = tradingDate.toString(),
            tradedQuantity = tradedQuantity,
            deliveryQuantity = deliveryQuantity,
            deliveryPercentage = deliveryPercentage,
            tradedQuantityPassed = tradedQuantityPassed,
            deliveryQuantityPassed = deliveryQuantityPassed,
            deliveryPercentagePassed = deliveryPercentagePassed,
            closePrice = trend.closePrice,
            sma50 = trend.sma50,
            sma200 = trend.sma200,
            sma50TwentySessionsAgo = trend.sma50TwentySessionsAgo,
            priceAboveSma50Passed = trend.priceAboveSma50Passed,
            sma50AboveSma200Passed = trend.sma50AboveSma200Passed,
            sma50RisingPassed = trend.sma50RisingPassed,
            uptrendMatched = trend.matched,
            trendDataStatus = trend.dataStatus,
            matched = tradedQuantityPassed &&
                deliveryQuantityPassed &&
                deliveryPercentagePassed &&
                trend.matched,
            dataStatus = dataStatus,
        )
    }

    private fun resolveDataStatus(delivery: StockDeliveryDaily?): AbsoluteDeliveryDataStatus {
        if (delivery == null) {
            return AbsoluteDeliveryDataStatus.NO_RECORD
        }
        if (delivery.reconciliationStatus == DeliveryReconciliationStatus.MISSING_FROM_SOURCE) {
            return AbsoluteDeliveryDataStatus.MISSING_FROM_SOURCE
        }
        if (delivery.ttlTrdQnty == null || delivery.delivQty == null || delivery.delivPer == null) {
            return AbsoluteDeliveryDataStatus.INCOMPLETE
        }
        return AbsoluteDeliveryDataStatus.AVAILABLE
    }
}
