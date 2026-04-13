package com.tradingtool.core.fundamentals.refresh

import com.tradingtool.core.delivery.model.DeliveryUniverse
import com.tradingtool.core.fundamentals.model.StockFundamentalsDaily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FundamentalsRefreshRunReportTest {

    @Test
    fun `create treats sub one percent failures as warnings`() {
        val result = FundamentalsRefreshResult(
            snapshotDate = LocalDate.parse("2026-04-14"),
            requestedSymbolsOverride = null,
            expectedSymbolCount = 200,
            successfulCount = 199,
            resolvedInstrumentTokens = listOf(1L, 2L),
            failures = listOf(
                FundamentalsRefreshFailure(
                    symbol = "SCHNEIDER",
                    reason = "Instrument token could not be resolved from Kite instruments.",
                ),
            ),
        )

        val report = FundamentalsRefreshRunReportFactory.create(
            result = result,
            rows = listOf(sampleRow("RELIANCE", 1L), sampleRow("TCS", 2L)),
        )

        assertTrue(report.blockingIssues.isEmpty())
        assertEquals(1, report.warningIssues.size)
        assertEquals(1, report.failedCount)
    }

    @Test
    fun `create blocks when failures are one percent or higher`() {
        val result = FundamentalsRefreshResult(
            snapshotDate = LocalDate.parse("2026-04-14"),
            requestedSymbolsOverride = listOf("RELIANCE", "TCS", "INFY"),
            expectedSymbolCount = 100,
            successfulCount = 98,
            resolvedInstrumentTokens = listOf(1L, 2L),
            failures = listOf(
                FundamentalsRefreshFailure(symbol = "ABC", reason = "404"),
                FundamentalsRefreshFailure(symbol = "XYZ", reason = "parse failed"),
            ),
        )

        val report = FundamentalsRefreshRunReportFactory.create(
            result = result,
            rows = listOf(sampleRow("RELIANCE", 1L), sampleRow("TCS", 2L)),
        )

        assertEquals(2, report.blockingIssues.size)
        assertTrue(report.warningIssues.isEmpty())
    }

    private fun sampleRow(symbol: String, instrumentToken: Long): StockFundamentalsDaily {
        return StockFundamentalsDaily(
            stockId = null,
            instrumentToken = instrumentToken,
            symbol = symbol,
            exchange = "NSE",
            universe = DeliveryUniverse.LARGEMIDCAP_250,
            snapshotDate = LocalDate.parse("2026-04-14"),
            companyName = symbol,
            marketCapCr = 100.0,
            stockPe = 10.0,
            rocePercent = 12.0,
            roePercent = 11.0,
            promoterHoldingPercent = 50.0,
            broadIndustry = "Industry",
            industry = "Sub Industry",
            sourceName = "screener",
            sourceUrl = "https://www.screener.in/company/$symbol/consolidated/",
            fetchedAt = null,
        )
    }
}
