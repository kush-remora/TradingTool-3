package com.tradingtool.core.delivery.reconciliation

import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.DeliverySourceRow
import com.tradingtool.core.delivery.model.StockDeliveryDaily

object DeliveryReconciliationAnalyzer {
    fun isDateComplete(
        expectedInstrumentTokens: Set<Long>,
        existingRows: List<StockDeliveryDaily>,
    ): Boolean {
        if (expectedInstrumentTokens.isEmpty()) {
            return true
        }

        val completedTokens = existingRows.asSequence()
            .filter { row ->
                row.reconciliationStatus == DeliveryReconciliationStatus.PRESENT ||
                    row.reconciliationStatus == DeliveryReconciliationStatus.MISSING_FROM_SOURCE
            }
            .map { row -> row.instrumentToken }
            .toSet()

        return completedTokens.containsAll(expectedInstrumentTokens)
    }

    fun selectBestRowsBySymbol(rows: List<DeliverySourceRow>): DeliverySourceRowsBySymbol {
        val tradingDate = rows.firstOrNull()?.tradingDate
            ?: error("Cannot select source rows from an empty delivery payload.")

        val rowsBySymbol = rows.groupBy { row -> row.symbol.uppercase() }
            .mapValues { (_, symbolRows) ->
                symbolRows.minWithOrNull(
                    compareBy<DeliverySourceRow> { row -> seriesPriority(row.series) }
                        .thenByDescending { row -> row.tradedQuantity },
                ) ?: error("Grouped symbol rows unexpectedly empty.")
            }

        return DeliverySourceRowsBySymbol(
            tradingDate = tradingDate,
            rowsBySymbol = rowsBySymbol,
        )
    }

    fun buildUpserts(
        expectations: List<DeliveryExpectation>,
        sourceRows: DeliverySourceRowsBySymbol,
        sourceFileName: String,
        sourceUrl: String,
    ): List<DeliveryReconciliationUpsert> {
        return expectations.map { expectation ->
            val sourceRow = sourceRows.rowsBySymbol[expectation.symbol.uppercase()]
            if (sourceRow == null) {
                DeliveryReconciliationUpsert(
                    stockId = expectation.stockId,
                    instrumentToken = expectation.instrumentToken,
                    symbol = expectation.symbol,
                    exchange = expectation.exchange,
                    universe = expectation.universe,
                    tradingDate = sourceRows.tradingDate,
                    reconciliationStatus = DeliveryReconciliationStatus.MISSING_FROM_SOURCE,
                    series = null,
                    ttlTrdQnty = null,
                    delivQty = null,
                    delivPer = null,
                    sourceFileName = sourceFileName,
                    sourceUrl = sourceUrl,
                )
            } else {
                DeliveryReconciliationUpsert(
                    stockId = expectation.stockId,
                    instrumentToken = expectation.instrumentToken,
                    symbol = expectation.symbol,
                    exchange = expectation.exchange,
                    universe = expectation.universe,
                    tradingDate = sourceRow.tradingDate,
                    reconciliationStatus = DeliveryReconciliationStatus.PRESENT,
                    series = sourceRow.series,
                    ttlTrdQnty = sourceRow.tradedQuantity,
                    delivQty = sourceRow.deliverableQuantity,
                    delivPer = sourceRow.deliveryPercent,
                    sourceFileName = sourceFileName,
                    sourceUrl = sourceUrl,
                )
            }
        }
    }

    private fun seriesPriority(series: String): Int {
        return when (series.uppercase()) {
            "EQ" -> 0
            "BE" -> 1
            else -> 2
        }
    }
}
