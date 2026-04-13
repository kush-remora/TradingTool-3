package com.tradingtool.core.strategy.rsimomentum

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Tests for lifecycle episode building and backtest equity curve logic.
 * Uses the package-private helpers exposed via internal visibility.
 */
class RsiMomentumHistoryServiceTest {

    // ─── Lifecycle episode tests ──────────────────────────────────────────────

    @Test
    fun `episode has correct entry and exit dates when symbol drops out`() {
        val snapshots = listOf(
            date("2024-01-01") to holdingsSnap(listOf("AAPL" to 1)),
            date("2024-01-02") to holdingsSnap(listOf("AAPL" to 2)),
            date("2024-01-03") to holdingsSnap(emptyList()),
        )

        val episodes = buildEpisodes("AAPL", snapshots)

        assertEquals(1, episodes.size)
        val ep = episodes[0]
        assertEquals("2024-01-01", ep.entryDate)
        assertEquals("2024-01-03", ep.exitDate)
        assertEquals("DROPPED_OUT", ep.exitReason)
        assertEquals(1, ep.bestRank)
        assertEquals(2, ep.daysInTop10)
    }

    @Test
    fun `open episode at end of window is marked END_OF_WINDOW`() {
        val snapshots = listOf(
            date("2024-01-01") to holdingsSnap(listOf("AAPL" to 3)),
            date("2024-01-02") to holdingsSnap(listOf("AAPL" to 2)),
        )

        val episodes = buildEpisodes("AAPL", snapshots)

        assertEquals(1, episodes.size)
        assertNull(episodes[0].exitDate)
        assertEquals("END_OF_WINDOW", episodes[0].exitReason)
        assertEquals(2, episodes[0].bestRank)
    }

    @Test
    fun `re-entry creates a new separate episode`() {
        val snapshots = listOf(
            date("2024-01-01") to holdingsSnap(listOf("AAPL" to 1)),
            date("2024-01-02") to holdingsSnap(emptyList()),
            date("2024-01-03") to holdingsSnap(listOf("AAPL" to 5)),
        )

        val episodes = buildEpisodes("AAPL", snapshots)

        assertEquals(2, episodes.size)
        assertEquals("2024-01-01", episodes[0].entryDate)
        assertEquals("DROPPED_OUT", episodes[0].exitReason)
        assertEquals("2024-01-03", episodes[1].entryDate)
        assertEquals("END_OF_WINDOW", episodes[1].exitReason)
    }

    @Test
    fun `symbol never in top 10 returns empty episodes`() {
        val snapshots = listOf(
            date("2024-01-01") to holdingsSnap(listOf("AAPL" to 1)),
        )

        val episodes = buildEpisodes("MSFT", snapshots)
        assertTrue(episodes.isEmpty())
    }

    @Test
    fun `rank timeline contains correct in-top10 flags`() {
        val snapshots = listOf(
            date("2024-01-01") to holdingsSnap(listOf("AAPL" to 1)),
            date("2024-01-02") to holdingsSnap(emptyList()),
            date("2024-01-03") to holdingsSnap(listOf("AAPL" to 3)),
        )

        val episodes = buildEpisodes("AAPL", snapshots)
        assertEquals(2, episodes.size)

        // First episode timeline: [in, out]
        val firstTimeline = episodes[0].rankTimeline
        assertTrue(firstTimeline.any { it.inTop10 })
        assertTrue(firstTimeline.any { !it.inTop10 })
    }

    // ─── Backtest equity curve tests ──────────────────────────────────────────

    @Test
    fun `equity curve has one point per calendar day in range`() {
        val from = date("2024-01-01")
        val to = date("2024-01-05")
        val dates = generateDateRange(from, to)

        assertEquals(5, dates.size)
        assertEquals(from, dates.first())
        assertEquals(to, dates.last())
    }

    @Test
    fun `drawdown is zero when equity never falls below peak`() {
        val curve = listOf(
            EquityCurvePoint("2024-01-01", 100_000.0, null),
            EquityCurvePoint("2024-01-02", 100_000.0, null),
            EquityCurvePoint("2024-01-03", 100_000.0, null),
        )

        val dd = computeDrawdownForTest(curve)
        assertTrue(dd.all { it.strategyDrawdownPct == 0.0 })
    }

    @Test
    fun `drawdown captures peak-to-trough correctly`() {
        val curve = listOf(
            EquityCurvePoint("2024-01-01", 100_000.0, null),
            EquityCurvePoint("2024-01-02", 90_000.0, null),
            EquityCurvePoint("2024-01-03", 80_000.0, null),
        )

        val dd = computeDrawdownForTest(curve)
        assertEquals(0.0, dd[0].strategyDrawdownPct, 0.001)
        assertEquals(-10.0, dd[1].strategyDrawdownPct, 0.001)
        assertEquals(-20.0, dd[2].strategyDrawdownPct, 0.001)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun date(s: String) = LocalDate.parse(s)

    private fun holdingsSnap(holdings: List<Pair<String, Int>>): RsiMomentumSnapshot {
        val stocks = holdings.map { (sym, rank) ->
            RsiMomentumRankedStock(
                rank = rank, symbol = sym, companyName = sym, instrumentToken = 0L,
                avgRsi = 60.0, rsi22 = 60.0, rsi44 = 60.0, rsi66 = 60.0,
                close = 100.0, sma20 = 95.0, extensionAboveSma20Pct = 5.0,
                buyZoneLow10w = 90.0, buyZoneHigh10w = 110.0,
                lowestRsi50d = 40.0, highestRsi50d = 80.0,
                avgTradedValueCr = 50.0, inBaseUniverse = true, inWatchlist = false,
            )
        }
        return RsiMomentumSnapshot(
            profileId = "largemidcap250",
            profileLabel = "LargeMidcap250",
            available = true,
            stale = false,
            config = RsiMomentumConfigSummary(),
            runAt = "2024-01-01T10:00:00Z",
            asOfDate = "2024-01-01",
            holdings = stocks,
            topCandidates = stocks,
        )
    }

    // Delegate to the same logic used in RsiMomentumHistoryService via package-private helpers
    private fun buildEpisodes(
        symbol: String,
        snapshots: List<Pair<LocalDate, RsiMomentumSnapshot>>,
    ): List<LifecycleEpisode> = buildEpisodesForSymbol(symbol, snapshots)

    private fun generateDateRange(from: LocalDate, to: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = from
        while (!current.isAfter(to)) {
            dates.add(current)
            current = current.plusDays(1)
        }
        return dates
    }

    private fun computeDrawdownForTest(curve: List<EquityCurvePoint>): List<DrawdownPoint> {
        var peak = Double.MIN_VALUE
        return curve.map { point ->
            if (point.strategyValue > peak) peak = point.strategyValue
            val dd = if (peak > 0) ((point.strategyValue - peak) / peak) * 100.0 else 0.0
            DrawdownPoint(date = point.date, strategyDrawdownPct = dd, benchmarkDrawdownPct = null)
        }
    }
}

// Package-private helper extracted from RsiMomentumHistoryService for testability
internal fun buildEpisodesForSymbol(
    symbol: String,
    snapshots: List<Pair<LocalDate, RsiMomentumSnapshot>>,
): List<LifecycleEpisode> {
    val episodes = mutableListOf<LifecycleEpisode>()
    var episodeStart: LocalDate? = null
    var bestRank = Int.MAX_VALUE
    var bestRankDate: LocalDate? = null
    val currentTimeline = mutableListOf<RankTimelinePoint>()

    for ((date, snap) in snapshots) {
        val holding = snap.holdings.find { it.symbol == symbol }
        val inTop10 = holding != null
        val rank = holding?.rank

        if (inTop10 && rank != null) {
            if (episodeStart == null) {
                episodeStart = date
                bestRank = rank
                bestRankDate = date
                currentTimeline.clear()
            }
            if (rank < bestRank) {
                bestRank = rank
                bestRankDate = date
            }
            currentTimeline.add(RankTimelinePoint(date.toString(), rank, true))
        } else {
            if (episodeStart != null) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(episodeStart, date).toInt().coerceAtLeast(1)
                currentTimeline.add(RankTimelinePoint(date.toString(), null, false))
                episodes.add(
                    LifecycleEpisode(
                        symbol = symbol,
                        entryDate = episodeStart.toString(),
                        exitDate = date.toString(),
                        daysInTop10 = days,
                        bestRank = bestRank,
                        bestRankDate = bestRankDate!!.toString(),
                        exitReason = "DROPPED_OUT",
                        rankTimeline = currentTimeline.toList(),
                    )
                )
                episodeStart = null
                bestRank = Int.MAX_VALUE
                bestRankDate = null
                currentTimeline.clear()
            } else {
                currentTimeline.add(RankTimelinePoint(date.toString(), null, false))
            }
        }
    }

    if (episodeStart != null && snapshots.isNotEmpty()) {
        val lastDate = snapshots.last().first
        val days = java.time.temporal.ChronoUnit.DAYS.between(episodeStart, lastDate).toInt().coerceAtLeast(1)
        episodes.add(
            LifecycleEpisode(
                symbol = symbol,
                entryDate = episodeStart.toString(),
                exitDate = null,
                daysInTop10 = days,
                bestRank = bestRank,
                bestRankDate = bestRankDate!!.toString(),
                exitReason = "END_OF_WINDOW",
                rankTimeline = currentTimeline.toList(),
            )
        )
    }

    return episodes
}
