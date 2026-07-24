package com.tradingtool.core.strategy.csvbacktest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CsvBacktestSignalDeduplicatorTest {

    @Test
    fun `keeps one signal per symbol and date`() {
        val signalDate = LocalDate.of(2026, 3, 20)
        val firstSignal = signal("TATAPOWER", signalDate, sector = "Power & Utilities")
        val duplicateSignal = signal("TATAPOWER", signalDate, sector = "Utilities")
        val laterSignal = signal("TATAPOWER", signalDate.plusDays(1), sector = "Power & Utilities")
        val otherSymbol = signal("SUNPHARMA", signalDate, sector = "Healthcare")

        val result = deduplicateCsvBacktestSignals(
            listOf(firstSignal, duplicateSignal, laterSignal, otherSymbol),
        )

        assertEquals(listOf(firstSignal, laterSignal, otherSymbol), result)
    }

    private fun signal(
        symbol: String,
        date: LocalDate,
        sector: String,
    ): CsvBacktestSignal = CsvBacktestSignal(
        symbol = symbol,
        date = date,
        marketCapName = "Largecap",
        sector = sector,
    )
}
