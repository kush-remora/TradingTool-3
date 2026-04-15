package com.tradingtool.core.strategy.rsimomentum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
            boardDisplayCount = 3,
            replacementPoolCount = 3,
            holdingCount = 2,
            maxExtensionAboveSma20ForNewEntry = 0.20,
            maxExtensionAboveSma20ForSkipNewEntry = 0.30,
        )

        assertEquals(listOf("AAA", "BBB", "CCC"), result.topCandidates.map { it.symbol })
        assertEquals(listOf("CCC", "AAA"), result.holdings.map { it.symbol })
        assertEquals(listOf("AAA"), result.rebalance.entries)
        assertEquals(emptyList<String>(), result.rebalance.exits)
        assertEquals(listOf("CCC"), result.rebalance.holds)
        assertEquals("ENTRY", result.topCandidates.first { it.symbol == "AAA" }.entryAction)
        assertEquals("WATCH", result.topCandidates.first { it.symbol == "BBB" }.entryAction)
        assertEquals("HOLD", result.topCandidates.first { it.symbol == "CCC" }.entryAction)
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
            boardDisplayCount = 2,
            replacementPoolCount = 2,
            holdingCount = 2,
            maxExtensionAboveSma20ForNewEntry = 0.20,
            maxExtensionAboveSma20ForSkipNewEntry = 0.30,
        )

        assertEquals(listOf("BBB", "AAA"), result.holdings.map { it.symbol })
        assertEquals(listOf("AAA"), result.rebalance.entries)
        assertEquals(listOf("DDD"), result.rebalance.exits)
        assertEquals(listOf("BBB"), result.rebalance.holds)
        assertEquals("HOLD", result.topCandidates.first { it.symbol == "BBB" }.entryAction)
        assertEquals("ENTRY", result.topCandidates.first { it.symbol == "AAA" }.entryAction)
    }

    @Test
    fun `rank marks pullback-watch entries and backfills with next eligible candidate`() {
        val metrics = listOf(
            metric(symbol = "AAA", avgRsi = 90.0, close = 121.0, sma20 = 100.0), // +21%
            metric(symbol = "BBB", avgRsi = 85.0, close = 118.0, sma20 = 100.0), // +18%
            metric(symbol = "CCC", avgRsi = 80.0, close = 112.0, sma20 = 100.0), // +12%
        )

        val result = RsiMomentumRanker.rank(
            metrics = metrics,
            previousHoldings = emptyList(),
            candidateCount = 3,
            boardDisplayCount = 3,
            replacementPoolCount = 3,
            holdingCount = 2,
            maxExtensionAboveSma20ForNewEntry = 0.20,
            maxExtensionAboveSma20ForSkipNewEntry = 0.30,
        )

        assertEquals(listOf("AAA", "BBB", "CCC"), result.topCandidates.map { it.symbol })
        assertEquals(listOf("BBB", "CCC"), result.holdings.map { it.symbol })
        assertTrue(result.topCandidates.first { it.symbol == "AAA" }.entryBlocked)
        assertEquals(
            "PRICE_EXTENSION_IN_PULLBACK_ZONE",
            result.topCandidates.first { it.symbol == "AAA" }.entryBlockReason,
        )
        assertEquals("WATCH_PULLBACK", result.topCandidates.first { it.symbol == "AAA" }.entryAction)
        assertFalse(result.topCandidates.first { it.symbol == "BBB" }.entryBlocked)
        assertEquals("ENTRY", result.topCandidates.first { it.symbol == "BBB" }.entryAction)
        assertEquals("ENTRY", result.topCandidates.first { it.symbol == "CCC" }.entryAction)
    }

    @Test
    fun `rank does not block retained holdings even when overstretched`() {
        val metrics = listOf(
            metric(symbol = "AAA", avgRsi = 90.0, close = 120.0, sma20 = 100.0), // +20%
            metric(symbol = "BBB", avgRsi = 80.0, close = 110.0, sma20 = 100.0),
        )

        val result = RsiMomentumRanker.rank(
            metrics = metrics,
            previousHoldings = listOf("AAA"),
            candidateCount = 2,
            boardDisplayCount = 2,
            replacementPoolCount = 2,
            holdingCount = 1,
            maxExtensionAboveSma20ForNewEntry = 0.20,
            maxExtensionAboveSma20ForSkipNewEntry = 0.30,
        )

        assertEquals(listOf("AAA"), result.holdings.map { it.symbol })
        assertFalse(result.topCandidates.first { it.symbol == "AAA" }.entryBlocked)
        assertEquals("HOLD", result.topCandidates.first { it.symbol == "AAA" }.entryAction)
    }

    @Test
    fun `rank keeps top candidates unchanged and fills holdings from below top candidates when skipped`() {
        val metrics = listOf(
            metric(symbol = "AAA", avgRsi = 95.0, close = 126.0, sma20 = 100.0), // blocked
            metric(symbol = "BBB", avgRsi = 90.0, close = 123.0, sma20 = 100.0), // blocked
            metric(symbol = "CCC", avgRsi = 85.0, close = 112.0, sma20 = 100.0), // eligible
            metric(symbol = "DDD", avgRsi = 80.0, close = 110.0, sma20 = 100.0), // eligible
        )

        val result = RsiMomentumRanker.rank(
            metrics = metrics,
            previousHoldings = emptyList(),
            candidateCount = 2,
            boardDisplayCount = 2,
            replacementPoolCount = 4,
            holdingCount = 2,
            maxExtensionAboveSma20ForNewEntry = 0.20,
            maxExtensionAboveSma20ForSkipNewEntry = 0.30,
        )

        assertEquals(listOf("AAA", "BBB"), result.topCandidates.map { it.symbol })
        assertEquals(listOf("CCC", "DDD"), result.holdings.map { it.symbol })
        assertEquals("WATCH_PULLBACK", result.topCandidates.first { it.symbol == "AAA" }.entryAction)
        assertEquals("WATCH_PULLBACK", result.topCandidates.first { it.symbol == "BBB" }.entryAction)
    }

    @Test
    fun `rank shows board size and keeps exits based on rebalance buffer`() {
        val metrics = listOf(
            metric(symbol = "AAA", avgRsi = 95.0),
            metric(symbol = "BBB", avgRsi = 94.0),
            metric(symbol = "CCC", avgRsi = 93.0),
            metric(symbol = "DDD", avgRsi = 92.0),
            metric(symbol = "EEE", avgRsi = 91.0),
        )

        val result = RsiMomentumRanker.rank(
            metrics = metrics,
            previousHoldings = listOf("EEE"),
            candidateCount = 2,
            boardDisplayCount = 4,
            replacementPoolCount = 4,
            holdingCount = 2,
            maxExtensionAboveSma20ForNewEntry = 0.20,
            maxExtensionAboveSma20ForSkipNewEntry = 0.30,
        )

        assertEquals(listOf("AAA", "BBB", "CCC", "DDD"), result.topCandidates.map { it.symbol })
        assertEquals(listOf("EEE"), result.rebalance.exits)
        assertEquals(listOf("AAA", "BBB"), result.holdings.map { it.symbol })
    }

    @Test
    fun `rank treats 20 percent extension boundary as eligible and above boundary as watch pullback`() {
        val metrics = listOf(
            metric(symbol = "AAA", avgRsi = 95.0, close = 120.0, sma20 = 100.0), // +20.00%
            metric(symbol = "BBB", avgRsi = 94.0, close = 120.01, sma20 = 100.0), // +20.01%
        )

        val result = RsiMomentumRanker.rank(
            metrics = metrics,
            previousHoldings = emptyList(),
            candidateCount = 2,
            boardDisplayCount = 2,
            replacementPoolCount = 2,
            holdingCount = 2,
            maxExtensionAboveSma20ForNewEntry = 0.20,
            maxExtensionAboveSma20ForSkipNewEntry = 0.30,
        )

        assertFalse(result.topCandidates.first { it.symbol == "AAA" }.entryBlocked)
        assertTrue(result.topCandidates.first { it.symbol == "BBB" }.entryBlocked)
        assertEquals("ENTRY", result.topCandidates.first { it.symbol == "AAA" }.entryAction)
        assertEquals("WATCH_PULLBACK", result.topCandidates.first { it.symbol == "BBB" }.entryAction)
    }

    @Test
    fun `rank marks skip when extension exceeds skip threshold`() {
        val metrics = listOf(
            metric(symbol = "AAA", avgRsi = 95.0, close = 130.01, sma20 = 100.0), // +30.01%
            metric(symbol = "BBB", avgRsi = 94.0, close = 119.0, sma20 = 100.0), // +19%
        )

        val result = RsiMomentumRanker.rank(
            metrics = metrics,
            previousHoldings = emptyList(),
            candidateCount = 2,
            boardDisplayCount = 2,
            replacementPoolCount = 2,
            holdingCount = 2,
            maxExtensionAboveSma20ForNewEntry = 0.20,
            maxExtensionAboveSma20ForSkipNewEntry = 0.30,
        )

        assertEquals("SKIP", result.topCandidates.first { it.symbol == "AAA" }.entryAction)
        assertEquals(
            "PRICE_EXTENSION_ABOVE_SKIP_THRESHOLD",
            result.topCandidates.first { it.symbol == "AAA" }.entryBlockReason,
        )
    }

    private fun metric(
        symbol: String,
        avgRsi: Double,
        close: Double = 100.0,
        sma20: Double = 100.0,
        buyZoneLow10w: Double = 90.0,
        buyZoneHigh10w: Double = 110.0,
        lowestRsi50d: Double = 35.0,
        highestRsi50d: Double = 75.0,
    ): SecurityMetrics = SecurityMetrics(
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
        close = close,
        sma20 = sma20,
        maxDailyMove5dPct = 2.0,
        buyZoneLow10w = buyZoneLow10w,
        buyZoneHigh10w = buyZoneHigh10w,
        lowestRsi50d = lowestRsi50d,
        highestRsi50d = highestRsi50d,
        avgTradedValueCr = 25.0,
    )
}
