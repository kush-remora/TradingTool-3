package com.tradingtool.core.delivery.validation

import com.tradingtool.core.delivery.model.DeliverySourceRow
import com.tradingtool.core.delivery.model.DeliverySourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeliverySourceValidationAnalyzerTest {
    private val tradingDate: LocalDate = LocalDate.of(2026, 4, 10)

    @Test
    fun `compare marks matching sources as materially consistent`() {
        val targetSymbols = setOf("RELIANCE", "INFY")
        val bhavRows = listOf(
            row("RELIANCE", 55.10, DeliverySourceType.BHAVDATA_FULL),
            row("INFY", 48.25, DeliverySourceType.BHAVDATA_FULL),
        )
        val mtoRows = listOf(
            row("RELIANCE", 55.10, DeliverySourceType.MTO),
            row("INFY", 48.26, DeliverySourceType.MTO),
        )

        val summary = DeliverySourceValidationAnalyzer.compare(bhavRows, mtoRows, targetSymbols)

        assertEquals(2, summary.comparedSymbolCount)
        assertTrue(summary.materiallyConsistent)
        assertTrue(summary.materialMismatches.isEmpty())
    }

    @Test
    fun `compare surfaces materially different delivery percentages`() {
        val targetSymbols = setOf("RELIANCE", "INFY")
        val bhavRows = listOf(
            row("RELIANCE", 55.10, DeliverySourceType.BHAVDATA_FULL),
            row("INFY", 48.25, DeliverySourceType.BHAVDATA_FULL),
        )
        val mtoRows = listOf(
            row("RELIANCE", 55.10, DeliverySourceType.MTO),
            row("INFY", 51.25, DeliverySourceType.MTO),
        )

        val summary = DeliverySourceValidationAnalyzer.compare(bhavRows, mtoRows, targetSymbols)

        assertEquals(1, summary.materialMismatches.size)
        assertFalse(summary.materiallyConsistent)
        assertEquals("INFY", summary.materialMismatches.first().symbol)
    }

    @Test
    fun `recommend prefers bhavcopy when coverage passes and comparison is acceptable`() {
        val targetSymbols = setOf("RELIANCE", "INFY", "TCS")
        val descriptor = DeliveryFileDescriptor(
            tradingDate = tradingDate,
            url = "https://example.com/file.csv",
            fileName = "file.csv",
        )
        val bhavAssessment = DeliverySourceValidationAnalyzer.summarize(
            source = DeliverySourceType.BHAVDATA_FULL,
            descriptor = descriptor,
            rows = listOf(
                row("RELIANCE", 55.10, DeliverySourceType.BHAVDATA_FULL),
                row("INFY", 48.25, DeliverySourceType.BHAVDATA_FULL),
                row("TCS", 60.00, DeliverySourceType.BHAVDATA_FULL),
            ),
            targetSymbols = targetSymbols,
        )
        val mtoAssessment = DeliverySourceValidationAnalyzer.summarize(
            source = DeliverySourceType.MTO,
            descriptor = descriptor.copy(fileName = "mto.dat"),
            rows = listOf(
                row("RELIANCE", 55.10, DeliverySourceType.MTO),
                row("INFY", 48.25, DeliverySourceType.MTO),
                row("TCS", 60.00, DeliverySourceType.MTO),
            ),
            targetSymbols = targetSymbols,
        )
        val comparison = DeliverySourceValidationAnalyzer.compare(
            bhavRows = listOf(
                row("RELIANCE", 55.10, DeliverySourceType.BHAVDATA_FULL),
                row("INFY", 48.25, DeliverySourceType.BHAVDATA_FULL),
                row("TCS", 60.00, DeliverySourceType.BHAVDATA_FULL),
            ),
            mtoRows = listOf(
                row("RELIANCE", 55.10, DeliverySourceType.MTO),
                row("INFY", 48.25, DeliverySourceType.MTO),
                row("TCS", 60.00, DeliverySourceType.MTO),
            ),
            targetSymbols = targetSymbols,
        )

        val recommendation = DeliverySourceValidationAnalyzer.recommend(
            bhavAssessment = bhavAssessment,
            mtoAssessment = mtoAssessment,
            comparison = comparison,
        )

        assertEquals(DeliveryRecommendation.BHAVDATA_FULL, recommendation)
    }

    @Test
    fun `recommend falls back to mto when bhav coverage is materially weaker`() {
        val targetSymbols = setOf("RELIANCE", "INFY", "TCS", "HDFCBANK")
        val descriptor = DeliveryFileDescriptor(
            tradingDate = tradingDate,
            url = "https://example.com/file.csv",
            fileName = "file.csv",
        )
        val bhavAssessment = DeliverySourceValidationAnalyzer.summarize(
            source = DeliverySourceType.BHAVDATA_FULL,
            descriptor = descriptor,
            rows = listOf(
                row("RELIANCE", 55.10, DeliverySourceType.BHAVDATA_FULL),
                row("INFY", 48.25, DeliverySourceType.BHAVDATA_FULL),
            ),
            targetSymbols = targetSymbols,
        )
        val mtoAssessment = DeliverySourceValidationAnalyzer.summarize(
            source = DeliverySourceType.MTO,
            descriptor = descriptor.copy(fileName = "mto.dat"),
            rows = listOf(
                row("RELIANCE", 55.10, DeliverySourceType.MTO),
                row("INFY", 48.25, DeliverySourceType.MTO),
                row("TCS", 60.00, DeliverySourceType.MTO),
                row("HDFCBANK", 49.00, DeliverySourceType.MTO),
            ),
            targetSymbols = targetSymbols,
        )

        val recommendation = DeliverySourceValidationAnalyzer.recommend(
            bhavAssessment = bhavAssessment,
            mtoAssessment = mtoAssessment,
            comparison = DeliveryComparisonSummary(materiallyConsistent = true),
        )

        assertEquals(DeliveryRecommendation.MTO, recommendation)
    }

    private fun row(
        symbol: String,
        deliveryPercent: Double,
        source: DeliverySourceType,
    ): DeliverySourceRow {
        return DeliverySourceRow(
            symbol = symbol,
            series = "EQ",
            tradingDate = tradingDate,
            tradedQuantity = 1000,
            deliverableQuantity = 500,
            deliveryPercent = deliveryPercent,
            source = source,
        )
    }
}
