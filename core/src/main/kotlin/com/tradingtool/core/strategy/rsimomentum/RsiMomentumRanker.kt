package com.tradingtool.core.strategy.rsimomentum

import com.tradingtool.core.technical.roundTo2
import java.time.LocalDate

object RsiMomentumRanker {
    private const val ENTRY_BLOCK_REASON_EXTENSION_ABOVE_SMA20_WATCH: String = "PRICE_EXTENSION_IN_PULLBACK_ZONE"
    private const val ENTRY_BLOCK_REASON_EXTENSION_ABOVE_SMA20_SKIP: String = "PRICE_EXTENSION_ABOVE_SKIP_THRESHOLD"
    private const val ENTRY_ACTION_SKIP: String = "SKIP"
    private const val ENTRY_ACTION_ENTRY: String = "ENTRY"
    private const val ENTRY_ACTION_HOLD: String = "HOLD"
    private const val ENTRY_ACTION_WATCH_PULLBACK: String = "WATCH_PULLBACK"
    private const val ENTRY_ACTION_WATCH: String = "WATCH"
    private const val ENTRY_STATE_ELIGIBLE: String = "ELIGIBLE"
    private const val ENTRY_STATE_WATCH_PULLBACK: String = "WATCH_PULLBACK"
    private const val ENTRY_STATE_SKIP: String = "SKIP"

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
        boardDisplayCount: Int,
        replacementPoolCount: Int,
        holdingCount: Int,
        maxExtensionAboveSma20ForNewEntry: Double,
        maxExtensionAboveSma20ForSkipNewEntry: Double,
        previousRanks: Map<String, Int> = emptyMap(),
    ): RankedPortfolio {
        val sorted = metrics
            .sortedWith(compareByDescending<SecurityMetrics> { metric -> metric.avgRsi }
                .thenBy { metric -> metric.member.symbol })

        val rankedBoard = sorted.mapIndexed { index, metric ->
            val symbol = metric.member.symbol.uppercase()
            metric.toRankedStock(
                rank = index + 1,
                rank5DaysAgo = previousRanks[symbol],
            )
        }

        val previousHoldingSet = previousHoldings.map { symbol -> symbol.uppercase() }.toSet()
        val flaggedBoard = rankedBoard
            .map { candidate ->
                val isPreviousHolding = candidate.symbol in previousHoldingSet
                val state = when {
                    isPreviousHolding -> ENTRY_STATE_ELIGIBLE
                    candidate.extensionAboveSma20Pct > (maxExtensionAboveSma20ForSkipNewEntry * 100.0) -> ENTRY_STATE_SKIP
                    candidate.extensionAboveSma20Pct > (maxExtensionAboveSma20ForNewEntry * 100.0) -> ENTRY_STATE_WATCH_PULLBACK
                    else -> ENTRY_STATE_ELIGIBLE
                }
                candidate.copy(
                    entryBlocked = state != ENTRY_STATE_ELIGIBLE,
                    entryBlockReason = when (state) {
                        ENTRY_STATE_SKIP -> ENTRY_BLOCK_REASON_EXTENSION_ABOVE_SMA20_SKIP
                        ENTRY_STATE_WATCH_PULLBACK -> ENTRY_BLOCK_REASON_EXTENSION_ABOVE_SMA20_WATCH
                        else -> null
                    },
                    entryAction = when (state) {
                        ENTRY_STATE_SKIP -> ENTRY_ACTION_SKIP
                        ENTRY_STATE_WATCH_PULLBACK -> ENTRY_ACTION_WATCH_PULLBACK
                        else -> ENTRY_ACTION_WATCH
                    },
                )
            }
        val rebalanceCandidates = flaggedBoard.take(candidateCount)
        val topCandidates = flaggedBoard.take(boardDisplayCount)
        val replacementPool = flaggedBoard.take(replacementPoolCount)
        val candidateSymbols = rebalanceCandidates.map { candidate -> candidate.symbol }.toSet()

        val retained = rebalanceCandidates.filter { candidate -> candidate.symbol in previousHoldingSet }
        val retainedSymbols = retained.map { candidate -> candidate.symbol }.toSet()

        val newSelections = replacementPool
            .filterNot { candidate -> candidate.symbol in retainedSymbols }
            .filterNot { candidate -> candidate.entryBlocked }
            .take((holdingCount - retained.size).coerceAtLeast(0))

        val targetWeightPct = if (holdingCount > 0) {
            ((100.0 / holdingCount) * 100.0).toInt() / 100.0
        } else {
            0.0
        }

        val rawHoldings = (retained + newSelections).take(holdingCount)

        val entries = rawHoldings.map { it.symbol }.filterNot { symbol -> symbol in previousHoldingSet }
        val exits = previousHoldings.map { symbol -> symbol.uppercase() }.filterNot { symbol -> symbol in candidateSymbols }
        val holds = rawHoldings.map { it.symbol }.filter { symbol -> symbol in previousHoldingSet }
        val entrySet = entries.toSet()
        val holdSet = holds.toSet()
        val holdings = rawHoldings.map { candidate ->
            val action = if (candidate.symbol in holdSet) ENTRY_ACTION_HOLD else ENTRY_ACTION_ENTRY
            candidate.copy(
                targetWeightPct = targetWeightPct,
                entryAction = action,
            )
        }
        val labeledTopCandidates = topCandidates.map { candidate ->
            val action = when {
                candidate.symbol in holdSet -> ENTRY_ACTION_HOLD
                candidate.symbol in entrySet -> ENTRY_ACTION_ENTRY
                candidate.entryAction == ENTRY_ACTION_SKIP -> ENTRY_ACTION_SKIP
                candidate.entryAction == ENTRY_ACTION_WATCH_PULLBACK -> ENTRY_ACTION_WATCH_PULLBACK
                else -> ENTRY_ACTION_WATCH
            }
            candidate.copy(entryAction = action)
        }

        return RankedPortfolio(
            topCandidates = labeledTopCandidates,
            holdings = holdings,
            rebalance = RsiMomentumRebalance(
                entries = entries,
                exits = exits,
                holds = holds,
            ),
            asOfDate = metrics.maxOfOrNull { metric -> metric.asOfDate },
        )
    }

    private fun SecurityMetrics.toRankedStock(rank: Int, rank5DaysAgo: Int? = null): RsiMomentumRankedStock = RsiMomentumRankedStock(
        rank = rank,
        rank5DaysAgo = rank5DaysAgo,
        rankImprovement = rank5DaysAgo?.let { it - rank },
        symbol = member.symbol,
        companyName = member.companyName,
        instrumentToken = member.instrumentToken,
        avgRsi = avgRsi,
        rsi22 = rsi22,
        rsi44 = rsi44,
        rsi66 = rsi66,
        close = close,
        sma20 = sma20,
        extensionAboveSma20Pct = extensionAboveSma20Pct(),
        maxDailyMove5dPct = maxDailyMove5dPct,
        buyZoneLow10w = buyZoneLow10w,
        buyZoneHigh10w = buyZoneHigh10w,
        lowestRsi50d = lowestRsi50d,
        highestRsi50d = highestRsi50d,
        avgTradedValueCr = avgTradedValueCr,
        inBaseUniverse = member.inBaseUniverse,
        inWatchlist = member.inWatchlist,
    )

    private fun SecurityMetrics.extensionAboveSma20Pct(): Double {
        if (sma20 <= 0.0) {
            return 0.0
        }
        return (((close / sma20) - 1.0) * 100.0).roundTo2()
    }
}
