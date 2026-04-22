package com.tradingtool.resources

import com.tradingtool.core.strategy.volume.EarningsFilterMode
import com.tradingtool.core.strategy.volume.VolumeSpikeBacktestRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VolumeSpikeBacktestRequestValidationTest {

    @Test
    fun `validate normalizes symbols and accepts custom window`() {
        val result = validateVolumeSpikeBacktestRequest(
            VolumeSpikeBacktestRequest(
                fromDate = "2026-03-01",
                toDate = "2026-03-20",
                delayMinutes = 10,
                manualSymbols = listOf(" infy ", "TCS", "INFY"),
                earningsFilterMode = EarningsFilterMode.CUSTOM_WINDOW,
                earningsWindowStartOffsetDays = -10,
                earningsWindowEndOffsetDays = -1,
            ),
        )

        assertEquals("2026-03-01", result.fromDate.toString())
        assertEquals("2026-03-20", result.toDate.toString())
        assertEquals(10, result.delayMinutes)
        assertEquals(listOf("INFY", "TCS"), result.manualSymbols)
        assertEquals(-10, result.earningsWindowStartOffsetDays)
        assertEquals(-1, result.earningsWindowEndOffsetDays)
    }

    @Test
    fun `validate fails when delay is missing`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateVolumeSpikeBacktestRequest(
                VolumeSpikeBacktestRequest(
                    fromDate = "2026-03-01",
                    toDate = "2026-03-20",
                    delayMinutes = null,
                ),
            )
        }

        assertEquals("delayMinutes is required.", error.message)
    }

    @Test
    fun `validate fails when custom window offsets are missing`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateVolumeSpikeBacktestRequest(
                VolumeSpikeBacktestRequest(
                    fromDate = "2026-03-01",
                    toDate = "2026-03-20",
                    delayMinutes = 5,
                    earningsFilterMode = EarningsFilterMode.CUSTOM_WINDOW,
                ),
            )
        }

        assertEquals(
            "earningsWindowStartOffsetDays and earningsWindowEndOffsetDays are required when earningsFilterMode=CUSTOM_WINDOW.",
            error.message,
        )
    }

    @Test
    fun `validate fails when manual mode has no symbols`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            validateVolumeSpikeBacktestRequest(
                VolumeSpikeBacktestRequest(
                    fromDate = "2026-03-01",
                    toDate = "2026-03-20",
                    delayMinutes = 5,
                    earningsFilterMode = EarningsFilterMode.MANUAL_SYMBOL,
                    manualSymbols = emptyList(),
                ),
            )
        }

        assertEquals(
            "manualSymbols must contain at least one symbol when earningsFilterMode=MANUAL_SYMBOL.",
            error.message,
        )
    }
}
