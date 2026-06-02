package com.tradingtool.core.delivery.reconciliation

import com.tradingtool.core.kite.InstrumentTokenResolution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeliveryReconciliationServiceFormatTest {

    @Test
    fun `formatUnresolvedDeliverySymbol prefers company name over broker expected keys`() {
        val formatted = formatUnresolvedDeliverySymbol(
            UnresolvedDeliverySymbol(
                symbol = "ISFL28",
                companyName = "IFL Enterprises",
                resolution = InstrumentTokenResolution(
                    exchange = "NSE",
                    symbol = "ISFL28",
                    expectedKeys = listOf("NSE:1003ISFL28", "NSE:1003ISFL28-BE"),
                    resolvedToken = null,
                    matchedKey = null,
                    candidateKeys = listOf("NSE:1003ISFL28", "NSE:1003ISFL28-BE"),
                ),
            ),
        )

        assertEquals(
            "ISFL28 [IFL Enterprises] (candidates=NSE:1003ISFL28 | NSE:1003ISFL28-BE)",
            formatted,
        )
    }

    @Test
    fun `formatUnresolvedDeliverySymbol falls back to symbol when company name missing`() {
        val formatted = formatUnresolvedDeliverySymbol(
            UnresolvedDeliverySymbol(
                symbol = "ISFL28",
                companyName = null,
                resolution = InstrumentTokenResolution(
                    exchange = "NSE",
                    symbol = "ISFL28",
                    expectedKeys = emptyList(),
                    resolvedToken = null,
                    matchedKey = null,
                    candidateKeys = emptyList(),
                ),
            ),
        )

        assertEquals(
            "ISFL28 [ISFL28] (candidates=none)",
            formatted,
        )
    }
}
