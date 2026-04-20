package com.tradingtool.resources

import com.tradingtool.core.strategy.profitlookback.ProfitLookbackRequest
import com.tradingtool.core.strategy.profitlookback.ProfitLookbackBulkRequest
import com.tradingtool.core.strategy.profitlookback.ProfitLookbackBulkRowRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProfitLookbackRequestValidationTest {
    @Test
    fun `normalizes valid request`() {
        val result = validateProfitLookbackRequest(
            ProfitLookbackRequest(
                symbol = "  infy  ",
                instrumentToken = 123L,
                sellDate = "2026-04-20",
                lookbackDays = 120,
                targetPercents = listOf(10.0, 5.0, -1.0, 10.0),
            ),
        )

        assertEquals("INFY", result.symbol)
        assertEquals(listOf(5.0, 10.0), result.targetPercents)
    }

    @Test
    fun `rejects invalid lookback`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validateProfitLookbackRequest(
                ProfitLookbackRequest(
                    symbol = "INFY",
                    instrumentToken = 123L,
                    sellDate = "2026-04-20",
                    lookbackDays = 0,
                    targetPercents = listOf(5.0),
                ),
            )
        }

        assertEquals("lookbackDays must be between 1 and 1000.", error.message)
    }

    @Test
    fun `rejects missing positive targets`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validateProfitLookbackRequest(
                ProfitLookbackRequest(
                    symbol = "INFY",
                    instrumentToken = 123L,
                    sellDate = "2026-04-20",
                    lookbackDays = 120,
                    targetPercents = listOf(-5.0, 0.0),
                ),
            )
        }

        assertEquals("targetPercents must contain at least one positive value.", error.message)
    }

    @Test
    fun `rejects invalid sell date format`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validateProfitLookbackRequest(
                ProfitLookbackRequest(
                    symbol = "INFY",
                    instrumentToken = 123L,
                    sellDate = "20-04-2026",
                    lookbackDays = 120,
                    targetPercents = listOf(5.0),
                ),
            )
        }

        assertEquals("sellDate must be in YYYY-MM-DD format.", error.message)
    }

    @Test
    fun `bulk validation normalizes valid rows and preserves row errors for partial success`() {
        val result = validateProfitLookbackBulkRequest(
            ProfitLookbackBulkRequest(
                lookbackDays = 120,
                targetPercents = listOf(10.0, 5.0, -1.0, 10.0),
                rows = listOf(
                    ProfitLookbackBulkRowRequest(
                        rowId = " row-1 ",
                        symbol = " infy ",
                        instrumentToken = 123L,
                        sellDate = "2026-04-20",
                    ),
                    ProfitLookbackBulkRowRequest(
                        rowId = "row-2",
                        symbol = "  ",
                        instrumentToken = 456L,
                        sellDate = "2026-04-20",
                    ),
                ),
            ),
        )

        assertEquals(120, result.lookbackDays)
        assertEquals(listOf(5.0, 10.0), result.targetPercents)
        assertEquals("row-1", result.rows[0].rowId)
        assertEquals("INFY", result.rows[0].request?.symbol)
        assertEquals(null, result.rows[0].error)
        assertEquals("symbol is required.", result.rows[1].error)
    }

    @Test
    fun `bulk validation rejects duplicate row ids`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validateProfitLookbackBulkRequest(
                ProfitLookbackBulkRequest(
                    lookbackDays = 120,
                    targetPercents = listOf(5.0),
                    rows = listOf(
                        ProfitLookbackBulkRowRequest("same", "INFY", 123L, "2026-04-20"),
                        ProfitLookbackBulkRowRequest("same", "RELIANCE", 456L, "2026-04-20"),
                    ),
                ),
            )
        }

        assertEquals("rows contain duplicate rowId values.", error.message)
    }
}
