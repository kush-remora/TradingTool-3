package com.tradingtool.core.strategy.rsimomentum

import java.time.LocalDate

object RsiMomentumRanker {

    data class RankedPortfolio(
        val topCandidates: List<RsiMomentumRankedStock>,
        val holdings: List<RsiMomentumRankedStock>,
        val rebalance: RsiMomentumRebalance,
        val asOfDate: LocalDate?,
    )

    fun rank(
        metrics: List<SecurityMetrics>,
        previousHoldings: List<String>,
        candidateCount: Int,
        holdingCount: Int,
    ): RankedPortfolio {
        val sorted = metrics
            .sortedWith(compareByDescending<SecurityMetrics> { metric -> metric.avgRsi }
                .thenBy { metric -> metric.member.symbol })

        val rankedBoard = sorted.mapIndexed { index, metric ->
            metric.toRankedStock(rank = index + 1)
        }

        val topCandidates = rankedBoard.take(candidateCount)
        val previousHoldingSet = previousHoldings.map { symbol -> symbol.uppercase() }.toSet()
        val candidateSymbols = topCandidates.map { candidate -> candidate.symbol }.toSet()

        val retained = topCandidates.filter { candidate -> candidate.symbol in previousHoldingSet }
        val retainedSymbols = retained.map { candidate -> candidate.symbol }.toSet()

        val newSelections = topCandidates
            .filterNot { candidate -> candidate.symbol in retainedSymbols }
            .take((holdingCount - retained.size).coerceAtLeast(0))

        val targetWeightPct = if (holdingCount > 0) {
            ((100.0 / holdingCount) * 100.0).toInt() / 100.0
        } else {
            0.0
        }

        val holdings = (retained + newSelections).take(holdingCount).map { candidate ->
            candidate.copy(targetWeightPct = targetWeightPct)
        }

        val entries = holdings.map { it.symbol }.filterNot { symbol -> symbol in previousHoldingSet }
        val exits = previousHoldings.map { symbol -> symbol.uppercase() }.filterNot { symbol -> symbol in candidateSymbols }
        val holds = holdings.map { it.symbol }.filter { symbol -> symbol in previousHoldingSet }

        return RankedPortfolio(
            topCandidates = topCandidates,
            holdings = holdings,
            rebalance = RsiMomentumRebalance(
                entries = entries,
                exits = exits,
                holds = holds,
            ),
            asOfDate = metrics.maxOfOrNull { metric -> metric.asOfDate },
        )
    }

    private fun SecurityMetrics.toRankedStock(rank: Int): RsiMomentumRankedStock = RsiMomentumRankedStock(
        rank = rank,
        symbol = member.symbol,
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
        avgRsi = avgRsi,
        rsi22 = rsi22,
        rsi44 = rsi44,
        rsi66 = rsi66,
        avgTradedValueCr = avgTradedValueCr,
        inBaseUniverse = member.inBaseUniverse,
        inWatchlist = member.inWatchlist,
    )
}
