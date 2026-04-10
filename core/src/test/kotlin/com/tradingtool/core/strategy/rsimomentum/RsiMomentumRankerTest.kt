package com.tradingtool.core.strategy.rsimomentum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RsiMomentumRankerTest {

    @Test
    fun `rank keeps prior holdings inside candidate buffer and fills from highest ranks`() {
        val metrics = listOf(
            metric(symbol = "AAA", avgRsi = 90.0),
            metric(symbol = "BBB", avgRsi = 80.0),
            metric(symbol = "CCC", avgRsi = 70.0),
            metric(symbol = "DDD", avgRsi = 60.0),
        )

        val result = RsiMomentumRanker.rank(
            metrics = metrics,
            previousHoldings = listOf("CCC"),
            candidateCount = 3,
            holdingCount = 2,
        )

        assertEquals(listOf("AAA", "BBB", "CCC"), result.topCandidates.map { it.symbol })
        assertEquals(listOf("CCC", "AAA"), result.holdings.map { it.symbol })
        assertEquals(listOf("AAA"), result.rebalance.entries)
        assertEquals(emptyList<String>(), result.rebalance.exits)
        assertEquals(listOf("CCC"), result.rebalance.holds)
    }

    @Test
    fun `rank exits holdings that fall outside top candidates`() {
        val metrics = listOf(
            metric(symbol = "AAA", avgRsi = 90.0),
            metric(symbol = "BBB", avgRsi = 80.0),
            metric(symbol = "CCC", avgRsi = 70.0),
        )

        val result = RsiMomentumRanker.rank(
            metrics = metrics,
            previousHoldings = listOf("DDD", "BBB"),
            candidateCount = 2,
            holdingCount = 2,
        )

        assertEquals(listOf("BBB", "AAA"), result.holdings.map { it.symbol })
        assertEquals(listOf("AAA"), result.rebalance.entries)
        assertEquals(listOf("DDD"), result.rebalance.exits)
        assertEquals(listOf("BBB"), result.rebalance.holds)
    }

    private fun metric(symbol: String, avgRsi: Double): SecurityMetrics = SecurityMetrics(
        member = UniverseMember(
            symbol = symbol,
            instrumentToken = symbol.hashCode().toLong(),
            companyName = "$symbol LTD",
            inBaseUniverse = true,
            inWatchlist = false,
        ),
        asOfDate = LocalDate.of(2026, 4, 10),
        avgRsi = avgRsi,
        rsi22 = avgRsi - 2,
        rsi44 = avgRsi,
        rsi66 = avgRsi + 2,
        avgTradedValueCr = 25.0,
    )
}

