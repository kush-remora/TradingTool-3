package com.tradingtool.core.delivery.reconciliation

import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeliveryReconciliationRunReportTest {
    private val tradingDate: LocalDate = LocalDate.of(2026, 4, 10)

    @Test
    fun `factory counts present missing and nullable stock rows`() {
        val report = DeliveryReconciliationRunReportFactory.create(
            requestedDate = null,
            result = DeliveryDateReconciliationResult(
                tradingDate = tradingDate,
                expectedCount = 3,
                alreadyComplete = false,
                fetchedFromSource = true,
                presentCount = 2,
                missingFromSourceCount = 1,
            ),
            rows = listOf(
                deliveryRow(symbol = "RELIANCE", instrumentToken = 101L, stockId = 1L, status = DeliveryReconciliationStatus.PRESENT, delivPer = 64.2),
                deliveryRow(symbol = "ABFRL", instrumentToken = 202L, stockId = null, status = DeliveryReconciliationStatus.PRESENT, delivPer = 51.3),
                deliveryRow(symbol = "XYZ", instrumentToken = 303L, stockId = null, status = DeliveryReconciliationStatus.MISSING_FROM_SOURCE, delivPer = null),
            ),
        )

        assertEquals(3, report.expectedSymbolCount)
        assertEquals(2, report.presentCount)
        assertEquals(1, report.missingFromSourceCount)
        assertEquals(2, report.nullableStockIdCount)
        assertEquals(1, report.watchlistLinkedCount)
        assertEquals(2, report.nonWatchlistCount)
        assertTrue(report.unresolvedIssues.isEmpty())
    }

    @Test
    fun `markdown and telegram summary include reconciliation state`() {
        val report = DeliveryReconciliationRunReportFactory.create(
            requestedDate = tradingDate,
            result = DeliveryDateReconciliationResult(
                tradingDate = tradingDate,
                expectedCount = 2,
                alreadyComplete = true,
                fetchedFromSource = false,
                presentCount = 1,
                missingFromSourceCount = 1,
            ),
            rows = listOf(
                deliveryRow(symbol = "RELIANCE", instrumentToken = 101L, stockId = 1L, status = DeliveryReconciliationStatus.PRESENT, delivPer = 64.2),
                deliveryRow(symbol = "XYZ", instrumentToken = 303L, stockId = null, status = DeliveryReconciliationStatus.MISSING_FROM_SOURCE, delivPer = null),
            ),
        )

        val markdown = report.toMarkdown()
        val telegramSummary = report.toTelegramSummary()

        assertTrue(markdown.contains("Requested date: `2026-04-10`"))
        assertTrue(markdown.contains("Already complete: `true`"))
        assertTrue(markdown.contains("### Missing From Source"))
        assertTrue(telegramSummary.contains("reconciled date: `2026-04-10`"))
        assertTrue(telegramSummary.contains("source fetch: `no (already complete)`"))
    }

    @Test
    fun `factory surfaces unresolved symbol issues`() {
        val report = DeliveryReconciliationRunReportFactory.create(
            requestedDate = null,
            result = DeliveryDateReconciliationResult(
                tradingDate = tradingDate,
                expectedCount = 3,
                alreadyComplete = false,
                fetchedFromSource = true,
                presentCount = 2,
                missingFromSourceCount = 0,
                unresolvedSymbols = listOf("SCHNEIDER"),
            ),
            rows = listOf(
                deliveryRow(symbol = "RELIANCE", instrumentToken = 101L, stockId = 1L, status = DeliveryReconciliationStatus.PRESENT, delivPer = 64.2),
                deliveryRow(symbol = "ABFRL", instrumentToken = 202L, stockId = null, status = DeliveryReconciliationStatus.PRESENT, delivPer = 51.3),
            ),
        )

        assertTrue(report.unresolvedIssues.any { issue -> issue.contains("SCHNEIDER") })
    }

    private fun deliveryRow(
        symbol: String,
        instrumentToken: Long,
        stockId: Long?,
        status: DeliveryReconciliationStatus,
        delivPer: Double?,
    ): StockDeliveryDaily {
        return StockDeliveryDaily(
            stockId = stockId,
            instrumentToken = instrumentToken,
            symbol = symbol,
            exchange = "NSE",
            tradingDate = tradingDate,
            reconciliationStatus = status,
            series = "EQ",
            ttlTrdQnty = 1000L,
            delivQty = if (delivPer == null) null else 500L,
            delivPer = delivPer,
            sourceFileName = "sec_bhavdata_full_10042026.csv",
            sourceUrl = "https://example.com/sec_bhavdata_full_10042026.csv",
            fetchedAt = null,
        )
    }
}
