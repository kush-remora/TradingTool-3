package com.tradingtool.core.volumeshocker.groww

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileGrowwVolumeShockerSourceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `fetchRows parses NSE rows and falls back to BSE`() = kotlinx.coroutines.runBlocking {
        val inputFile = tempDir.resolve("groww-volume-shocker.json")
        Files.writeString(
            inputFile,
            payload(
                stockJson(nseSymbol = "INFY", bseSymbol = "500209", companyName = "Infosys"),
                stockJson(nseSymbol = "", bseSymbol = "539607", companyName = "Blue Cloud"),
            ),
        )

        val rows = FileGrowwVolumeShockerSource(inputFile, ObjectMapper()).fetchRows()

        assertEquals(2, rows.size)
        assertEquals("NSE", rows[0].exchange)
        assertEquals("INFY", rows[0].symbol)
        assertEquals("BSE", rows[1].exchange)
        assertEquals("539607", rows[1].symbol)
        assertEquals(2, rows[1].sourceRank)
    }

    @Test
    fun `fetchRows rejects stripped Groww identity fields`() {
        val inputFile = tempDir.resolve("groww-volume-shocker.json")
        Files.writeString(
            inputFile,
            payload(stockJson(nseSymbol = "INFY", bseSymbol = "500209", companyName = "")),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                FileGrowwVolumeShockerSource(inputFile, ObjectMapper()).fetchRows()
            }
        }

        assertEquals("Groww volume-shocker row 1 has empty companyName.", error.message)
    }

    private fun payload(vararg stocks: String): String {
        return """{"data":{"stocks":[${stocks.joinToString(",")} ]}}"""
    }

    private fun stockJson(
        nseSymbol: String,
        bseSymbol: String,
        companyName: String,
    ): String {
        return """
            {
              "isin":"INE000000001",
              "gsin":"GSTK500001",
              "companyName":"$companyName",
              "searchId":"example-company",
              "nseScriptCode":"$nseSymbol",
              "bseScriptCode":"$bseSymbol",
              "type":"STOCKS",
              "ltp":100.25,
              "marketCap":50000.00,
              "volumeWeekAvg":100000.50,
              "close":95.75,
              "yearLow":75.00,
              "yearHigh":125.00,
              "volume":1000000
            }
        """.trimIndent()
    }
}
