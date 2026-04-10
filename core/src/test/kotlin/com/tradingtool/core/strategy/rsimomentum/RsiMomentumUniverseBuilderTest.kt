package com.tradingtool.core.strategy.rsimomentum

import com.tradingtool.core.model.stock.Stock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RsiMomentumUniverseBuilderTest {

    @Test
    fun `build unions base preset and watchlist without duplicates`() {
        val baseSymbols = listOf("RELIANCE", "INFY")
        val watchlistStocks = listOf(
            stock(symbol = "INFY", instrumentToken = 11L),
            stock(symbol = "TCS", instrumentToken = 22L),
        )

        val result = RsiMomentumUniverseBuilder.build(
            baseSymbols = baseSymbols,
            watchlistStocks = watchlistStocks,
            tokenLookup = { symbol -> if (symbol == "RELIANCE") 1L else null },
            companyNameLookup = { symbol -> "$symbol LTD" },
        )

        assertEquals(listOf("RELIANCE", "INFY", "TCS"), result.members.map { it.symbol })
        assertEquals(2, result.baseUniverseCount)
        assertEquals(2, result.watchlistCount)
        assertEquals(1, result.watchlistAdditionsCount)
        assertTrue(result.unresolvedSymbols.isEmpty())
    }

    @Test
    fun `build excludes unresolved symbols cleanly`() {
        val result = RsiMomentumUniverseBuilder.build(
            baseSymbols = listOf("UNKNOWN"),
            watchlistStocks = emptyList(),
            tokenLookup = { null },
            companyNameLookup = { null },
        )

        assertTrue(result.members.isEmpty())
        assertEquals(listOf("UNKNOWN"), result.unresolvedSymbols)
    }

    private fun stock(symbol: String, instrumentToken: Long): Stock = Stock(
        id = instrumentToken,
        symbol = symbol,
        instrumentToken = instrumentToken,
        companyName = "$symbol LTD",
        exchange = "NSE",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )
}

