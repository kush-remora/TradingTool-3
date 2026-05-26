package com.tradingtool.resources

import com.tradingtool.core.strategy.wyckoff.phase1.WyckoffPhase1RunRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class WyckoffPhase1RequestValidationTest {

    @Test
    fun `validate normalizes and dedupes symbols and universe keys`() {
        val result = validateWyckoffPhase1RunRequest(
            WyckoffPhase1RunRequest(
                universeKeys = listOf(" WATCHLIST ", "watchlist", "NIFTY_50", "NIFTY_50"),
                symbols = listOf(" infy ", "TCS", "infy", "  "),
            ),
        )

        assertEquals(listOf("WATCHLIST", "NIFTY_50"), result.universeKeys)
        assertEquals(listOf("INFY", "TCS"), result.symbols)
    }

    @Test
    fun `validate fails when universe keys missing`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateWyckoffPhase1RunRequest(
                WyckoffPhase1RunRequest(
                    universeKeys = emptyList(),
                    symbols = listOf("INFY"),
                ),
            )
        }

        assertEquals("universeKeys must contain at least one value.", error.message)
    }

    @Test
    fun `validate fails when asOfDate invalid`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateWyckoffPhase1RunRequest(
                WyckoffPhase1RunRequest(
                    universeKeys = listOf("WATCHLIST"),
                    asOfDate = "26-05-2026",
                ),
            )
        }

        assertEquals("asOfDate must be in YYYY-MM-DD format.", error.message)
    }
}
