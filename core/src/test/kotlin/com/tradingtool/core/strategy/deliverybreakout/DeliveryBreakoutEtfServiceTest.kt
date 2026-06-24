package com.tradingtool.core.strategy.deliverybreakout

import com.tradingtool.core.delivery.model.DeliveryReconciliationStatus
import com.tradingtool.core.delivery.model.StockDeliveryDaily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class DeliveryBreakoutEtfServiceTest {

    @Test
    fun `parseEtfSymbols reads symbol column and normalizes case`() {
        val symbols = DeliveryBreakoutEtfService().parseEtfSymbols(
            """
            Symbol,Underlying,SecurityName
            NIFTYBEES,Nifty50,ETF One
            goldcase,Gold,ETF Two
            """.trimIndent(),
        )

        assertEquals(setOf("NIFTYBEES", "GOLDCASE"), symbols)
    }

    @Test
    fun `filterNonEtfRows removes rows whose symbols are present in ETF csv`() {
        withTempEtfFile(
            """
            Symbol,Underlying,SecurityName
            NIFTYBEES,Nifty50,ETF One
            """.trimIndent(),
        ) {
            val service = DeliveryBreakoutEtfService()
            val filteredRows = service.filterNonEtfRows(
                listOf(
                    deliveryRow(symbol = "NIFTYBEES", token = 101L),
                    deliveryRow(symbol = "RELIANCE", token = 202L),
                ),
            )

            assertEquals(1, filteredRows.size)
            assertEquals("RELIANCE", filteredRows.single().symbol)
        }
    }

    @Test
    fun `filterNonEtfRows leaves rows unchanged when ETF file is missing`() {
        val previous = System.getProperty(DeliveryBreakoutEtfService.ETF_FILE_PATH_PROPERTY)
        System.setProperty(DeliveryBreakoutEtfService.ETF_FILE_PATH_PROPERTY, "manual-input/missing-etf-file.csv")
        try {
            val rows = listOf(deliveryRow(symbol = "RELIANCE", token = 202L))
            val filteredRows = DeliveryBreakoutEtfService().filterNonEtfRows(rows)

            assertEquals(rows, filteredRows)
            assertTrue(filteredRows.isNotEmpty())
        } finally {
            restoreSystemProperty(
                key = DeliveryBreakoutEtfService.ETF_FILE_PATH_PROPERTY,
                previousValue = previous,
            )
        }
    }

    private fun withTempEtfFile(csvBody: String, block: () -> Unit) {
        val tempFile = kotlin.io.path.createTempFile(prefix = "delivery-breakout-etf-", suffix = ".csv")
        val previous = System.getProperty(DeliveryBreakoutEtfService.ETF_FILE_PATH_PROPERTY)
        tempFile.toFile().writeText(csvBody)
        System.setProperty(DeliveryBreakoutEtfService.ETF_FILE_PATH_PROPERTY, tempFile.toString())
        try {
            block()
        } finally {
            restoreSystemProperty(
                key = DeliveryBreakoutEtfService.ETF_FILE_PATH_PROPERTY,
                previousValue = previous,
            )
            tempFile.toFile().delete()
        }
    }

    private fun restoreSystemProperty(key: String, previousValue: String?) {
        if (previousValue == null) {
            System.clearProperty(key)
        } else {
            System.setProperty(key, previousValue)
        }
    }

    private fun deliveryRow(symbol: String, token: Long): StockDeliveryDaily {
        return StockDeliveryDaily(
            instrumentToken = token,
            symbol = symbol,
            exchange = "NSE",
            universe = "watchlist",
            tradingDate = LocalDate.of(2026, 6, 24),
            reconciliationStatus = DeliveryReconciliationStatus.PRESENT,
            series = "EQ",
            ttlTrdQnty = 100_000L,
            delivQty = 60_000L,
            delivPer = 60.0,
            sourceFileName = null,
            sourceUrl = null,
            fetchedAt = OffsetDateTime.parse("2026-06-24T12:00:00Z"),
        )
    }
}
