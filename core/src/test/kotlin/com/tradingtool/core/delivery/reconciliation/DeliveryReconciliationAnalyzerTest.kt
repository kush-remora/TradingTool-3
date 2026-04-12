package com.tradingtool.core.delivery.reconciliation

import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.DeliverySourceRow
import com.tradingtool.core.delivery.model.DeliverySourceType
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import com.tradingtool.core.delivery.model.DeliveryUniverse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeliveryReconciliationAnalyzerTest {
    private val tradingDate: LocalDate = LocalDate.of(2026, 4, 10)

    @Test
    fun `isDateComplete returns true when every expected token has a row`() {
        val expectedTokens = setOf(101L, 202L, 303L)
        val existingRows = listOf(
            deliveryRow(101L, DeliveryReconciliationStatus.PRESENT),
            deliveryRow(202L, DeliveryReconciliationStatus.MISSING_FROM_SOURCE),
            deliveryRow(303L, DeliveryReconciliationStatus.PRESENT),
        )

        assertTrue(DeliveryReconciliationAnalyzer.isDateComplete(expectedTokens, existingRows))
    }

    @Test
    fun `isDateComplete returns false when any expected token is absent`() {
        val expectedTokens = setOf(101L, 202L, 303L)
        val existingRows = listOf(
            deliveryRow(101L, DeliveryReconciliationStatus.PRESENT),
            deliveryRow(202L, DeliveryReconciliationStatus.MISSING_FROM_SOURCE),
        )

        assertFalse(DeliveryReconciliationAnalyzer.isDateComplete(expectedTokens, existingRows))
    }

    @Test
    fun `selectBestRowsBySymbol prefers EQ series and higher traded quantity`() {
        val rowsBySymbol = DeliveryReconciliationAnalyzer.selectBestRowsBySymbol(
            listOf(
                sourceRow(symbol = "ABC", series = "BE", tradedQuantity = 500),
                sourceRow(symbol = "ABC", series = "EQ", tradedQuantity = 300),
                sourceRow(symbol = "XYZ", series = "EQ", tradedQuantity = 1000),
            ),
        )

        assertEquals("EQ", rowsBySymbol.rowsBySymbol["ABC"]?.series)
        assertEquals(300L, rowsBySymbol.rowsBySymbol["ABC"]?.tradedQuantity)
        assertEquals(1000L, rowsBySymbol.rowsBySymbol["XYZ"]?.tradedQuantity)
    }

    @Test
    fun `buildUpserts creates present and missing rows from source comparison`() {
        val expectations = listOf(
            DeliveryExpectation(stockId = 1L, instrumentToken = 101L, symbol = "RELIANCE", exchange = "NSE", universe = DeliveryUniverse.LARGEMIDCAP_250),
            DeliveryExpectation(stockId = null, instrumentToken = 202L, symbol = "ABFRL", exchange = "NSE", universe = DeliveryUniverse.SMALLCAP_250),
        )
        val sourceRows = DeliverySourceRowsBySymbol(
            tradingDate = tradingDate,
            rowsBySymbol = mapOf(
                "RELIANCE" to sourceRow(symbol = "RELIANCE", series = "EQ", tradedQuantity = 1000, deliverableQuantity = 600, deliveryPercent = 60.0),
            ),
        )

        val upserts = DeliveryReconciliationAnalyzer.buildUpserts(
            expectations = expectations,
            sourceRows = sourceRows,
            sourceFileName = "sec_bhavdata_full_10042026.csv",
            sourceUrl = "https://example.com/sec_bhavdata_full_10042026.csv",
        )

        assertEquals(2, upserts.size)
        assertEquals(DeliveryReconciliationStatus.PRESENT, upserts[0].reconciliationStatus)
        assertEquals(DeliveryReconciliationStatus.MISSING_FROM_SOURCE, upserts[1].reconciliationStatus)
        assertEquals(60.0, upserts[0].delivPer)
        assertNull(upserts[1].delivPer)
    }

    private fun deliveryRow(
        instrumentToken: Long,
        status: DeliveryReconciliationStatus,
    ): StockDeliveryDaily {
        return StockDeliveryDaily(
            stockId = null,
            instrumentToken = instrumentToken,
            symbol = "SYM$instrumentToken",
            exchange = "NSE",
            universe = DeliveryUniverse.LARGEMIDCAP_250,
            tradingDate = tradingDate,
            reconciliationStatus = status,
            series = "EQ",
            ttlTrdQnty = 1000L,
            delivQty = 500L,
            delivPer = 50.0,
            sourceFileName = "file.csv",
            sourceUrl = "https://example.com/file.csv",
            fetchedAt = null,
        )
    }

    private fun sourceRow(
        symbol: String,
        series: String,
        tradedQuantity: Long,
        deliverableQuantity: Long = 500L,
        deliveryPercent: Double = 50.0,
    ): DeliverySourceRow {
        return DeliverySourceRow(
            symbol = symbol,
            series = series,
            tradingDate = tradingDate,
            tradedQuantity = tradedQuantity,
            deliverableQuantity = deliverableQuantity,
            deliveryPercent = deliveryPercent,
            source = DeliverySourceType.BHAVDATA_FULL,
        )
    }
}
