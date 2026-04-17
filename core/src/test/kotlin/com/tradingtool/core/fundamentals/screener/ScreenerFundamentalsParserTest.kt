package com.tradingtool.core.fundamentals.screener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScreenerFundamentalsParserTest {
    @Test
    fun `parse extracts fundamentals from NSE payload json`() {
        val payload = NseFundamentalsPayload(
            symbol = "INFY",
            quoteJson = """
                {
                  "info": {
                    "companyName": "Infosys Limited"
                  },
                  "metadata": {
                    "industry": "Computers - Software & Consulting",
                    "pdSymbolPe": 19.11
                  },
                  "industryInfo": {
                    "macro": "Information Technology",
                    "sector": "Information Technology",
                    "industry": "IT - Software"
                  },
                  "securityInfo": {
                    "issuedSize": 4055623379
                  },
                  "priceInfo": {
                    "lastPrice": 1320
                  }
                }
            """.trimIndent(),
            financialResultsJson = """
                [
                  {
                    "symbol": "INFY",
                    "companyName": "Infosys Limited",
                    "industry": "Computers - Software & Consulting",
                    "xbrl": "https://nsearchives.nseindia.com/corporate/xbrl/INDAS_117294_1348219_16012025074210.xml"
                  }
                ]
            """.trimIndent(),
            shareholdingJson = """
                [
                  {
                    "symbol": "INFY",
                    "pr_and_prgrp": "14.38"
                  }
                ]
            """.trimIndent(),
            sourceUrl = "https://www.nseindia.com/api/corporates-financial-results?index=equities&symbol=INFY&period=Quarterly",
        )

        val jsonPayload = objectMapper.writeValueAsString(payload)
        val snapshot = ScreenerFundamentalsParser.parse("INFY", jsonPayload)

        assertEquals("Infosys Limited", snapshot.companyName)
        assertEquals(535342.286, snapshot.marketCapCr ?: 0.0, 0.001)
        assertEquals(19.11, snapshot.stockPe)
        assertEquals(14.38, snapshot.promoterHoldingPercent)
        assertEquals("Information Technology", snapshot.broadIndustry)
        assertEquals("Computers - Software & Consulting", snapshot.industry)
        assertNull(snapshot.rocePercent)
        assertNull(snapshot.roePercent)
        assertNull(snapshot.debtToEquity)
        assertNull(snapshot.pledgedPercent)
    }

    private companion object {
        val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    }
}
