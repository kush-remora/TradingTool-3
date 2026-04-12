package com.tradingtool.core.delivery.reconciliation

import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.DeliverySourceRow
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import java.time.LocalDate

data class DeliveryExpectation(
    val stockId: Long?,
    val instrumentToken: Long,
    val symbol: String,
    val exchange: String,
)

data class DeliveryReconciliationUpsert(
    val stockId: Long?,
    val instrumentToken: Long,
    val symbol: String,
    val exchange: String,
    val tradingDate: LocalDate,
    val reconciliationStatus: DeliveryReconciliationStatus,
    val series: String?,
    val ttlTrdQnty: Long?,
    val delivQty: Long?,
    val delivPer: Double?,
    val sourceFileName: String?,
    val sourceUrl: String?,
)

data class DeliveryDateReconciliationResult(
    val tradingDate: LocalDate,
    val expectedCount: Int,
    val alreadyComplete: Boolean,
    val fetchedFromSource: Boolean,
    val presentCount: Int,
    val missingFromSourceCount: Int,
    val unresolvedSymbols: List<String> = emptyList(),
)

data class DeliverySourceRowsBySymbol(
    val tradingDate: LocalDate,
    val rowsBySymbol: Map<String, DeliverySourceRow>,
)

data class DeliveryReconciliationRunReport(
    val requestedDate: LocalDate?,
    val resolvedDate: LocalDate,
    val expectedSymbolCount: Int,
    val presentCount: Int,
    val missingFromSourceCount: Int,
    val nullableStockIdCount: Int,
    val watchlistLinkedCount: Int,
    val nonWatchlistCount: Int,
    val fetchedFromSource: Boolean,
    val alreadyComplete: Boolean,
    val unresolvedIssues: List<String>,
    val presentSamples: List<DeliveryReconciliationSampleRow>,
    val missingFromSourceSamples: List<DeliveryReconciliationSampleRow>,
    val nullableStockIdSamples: List<DeliveryReconciliationSampleRow>,
)

data class DeliveryReconciliationSampleRow(
    val instrumentToken: Long,
    val symbol: String,
    val stockId: Long?,
    val reconciliationStatus: DeliveryReconciliationStatus,
    val delivPer: Double?,
)

object DeliveryReconciliationRunReportFactory {
    fun create(
        requestedDate: LocalDate?,
        result: DeliveryDateReconciliationResult,
        rows: List<StockDeliveryDaily>,
    ): DeliveryReconciliationRunReport {
        val presentRows = rows.filter { row -> row.reconciliationStatus == DeliveryReconciliationStatus.PRESENT }
        val missingRows = rows.filter { row -> row.reconciliationStatus == DeliveryReconciliationStatus.MISSING_FROM_SOURCE }
        val nullableStockRows = rows.filter { row -> row.stockId == null }
        val unresolvedIssues = buildList {
            result.unresolvedSymbols.forEach { symbol ->
                add("Instrument token could not be resolved for configured symbol $symbol.")
            }
            if (rows.size != result.expectedCount) {
                add("Expected ${result.expectedCount} reconciled rows but found ${rows.size} rows in stock_delivery_daily.")
            }
            if (presentRows.size != result.presentCount) {
                add("Present row count mismatch: service=${result.presentCount}, table=${presentRows.size}.")
            }
            if (missingRows.size != result.missingFromSourceCount) {
                add("Missing-from-source row count mismatch: service=${result.missingFromSourceCount}, table=${missingRows.size}.")
            }
        }

        return DeliveryReconciliationRunReport(
            requestedDate = requestedDate,
            resolvedDate = result.tradingDate,
            expectedSymbolCount = result.expectedCount,
            presentCount = presentRows.size,
            missingFromSourceCount = missingRows.size,
            nullableStockIdCount = nullableStockRows.size,
            watchlistLinkedCount = rows.size - nullableStockRows.size,
            nonWatchlistCount = nullableStockRows.size,
            fetchedFromSource = result.fetchedFromSource,
            alreadyComplete = result.alreadyComplete,
            unresolvedIssues = unresolvedIssues,
            presentSamples = presentRows.take(5).map(::toSampleRow),
            missingFromSourceSamples = missingRows.take(5).map(::toSampleRow),
            nullableStockIdSamples = nullableStockRows.take(5).map(::toSampleRow),
        )
    }

    private fun toSampleRow(row: StockDeliveryDaily): DeliveryReconciliationSampleRow {
        return DeliveryReconciliationSampleRow(
            instrumentToken = row.instrumentToken,
            symbol = row.symbol,
            stockId = row.stockId,
            reconciliationStatus = row.reconciliationStatus,
            delivPer = row.delivPer,
        )
    }
}
