package com.tradingtool.cron

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DeliveryReconciliationJobTest {

    @Test
    fun `summarizeBackfillFailures returns readable date reasons`() {
        val summary = summarizeBackfillFailures(
            listOf(
                DeliveryBackfillFailure(
                    tradingDate = LocalDate.parse("2025-02-27"),
                    reason = "Instrument token could not be resolved for configured symbol ABC",
                ),
                DeliveryBackfillFailure(
                    tradingDate = LocalDate.parse("2025-02-28"),
                    reason = "Failed to download file",
                ),
            ),
        )

        assertEquals(
            "2025-02-27: Instrument token could not be resolved for configured symbol ABC | 2025-02-28: Failed to download file",
            summary,
        )
    }

    @Test
    fun `summarizeBackfillFailures returns none when empty`() {
        assertEquals("none", summarizeBackfillFailures(emptyList()))
    }
}
