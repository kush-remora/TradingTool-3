package com.tradingtool.core.strategy.twodaygreen

import com.tradingtool.core.candle.DailyCandle
import kotlin.math.round

class TwoDayGreenCandleBacktestEngine {
    internal fun run(
        member: TwoDayGreenCandleMember,
        candles: List<DailyCandle>,
        testSessionCount: Int = TEST_SESSION_COUNT,
    ): TwoDayGreenCandleSymbolReport {
        val sortedCandles = candles.sortedBy(DailyCandle::candleDate)
        require(sortedCandles.size >= MINIMUM_CANDLE_COUNT) {
            "At least $MINIMUM_CANDLE_COUNT daily candles are required for ${member.symbol}."
        }

        val testedCandles = sortedCandles.takeLast(testSessionCount)
        val firstTestIndex = sortedCandles.indexOfFirst { candle -> candle.candleDate == testedCandles.first().candleDate }
        val lastTestIndex = firstTestIndex + testedCandles.lastIndex
        val trades = ((firstTestIndex + 2)..lastTestIndex).mapNotNull { buyIndex ->
            createTrade(member, sortedCandles, buyIndex, lastTestIndex)
        }

        return TwoDayGreenCandleSymbolReport(
            symbol = member.symbol,
            companyName = member.companyName,
            testedFromDate = testedCandles.first().candleDate.toString(),
            testedToDate = testedCandles.last().candleDate.toString(),
            summary = summarize(trades),
            trades = trades,
        )
    }

    private fun createTrade(
        member: TwoDayGreenCandleMember,
        candles: List<DailyCandle>,
        buyIndex: Int,
        lastTestIndex: Int,
    ): TwoDayGreenCandleBacktestTrade? {
        val setupDayOne = candles[buyIndex - 2]
        val setupDayTwo = candles[buyIndex - 1]
        val buyDay = candles[buyIndex]
        val setupDayOneChange = dailyChangePct(candles, buyIndex - 2) ?: return null
        val setupDayTwoChange = dailyChangePct(candles, buyIndex - 1) ?: return null

        if (!isQualifyingSetup(setupDayOne, setupDayOneChange) || !isQualifyingSetup(setupDayTwo, setupDayTwoChange)) {
            return null
        }

        val entryPrice = buyDay.open
        if (entryPrice <= 0.0) return null

        val targetPrice = entryPrice * (1.0 + TARGET_PCT / 100.0)
        val targetIndex = (buyIndex..lastTestIndex).firstOrNull { index -> candles[index].high >= targetPrice }
        val outcomeEndIndex = targetIndex ?: lastTestIndex
        val maximumHighSinceEntryPct = candles
            .subList(buyIndex, outcomeEndIndex + 1)
            .maxOf { candle -> ((candle.high - entryPrice) / entryPrice) * 100.0 }

        return TwoDayGreenCandleBacktestTrade(
            symbol = member.symbol,
            instrumentToken = member.instrumentToken,
            setupDayOne = setupDayOne.toObservation(candles, buyIndex - 2),
            setupDayTwo = setupDayTwo.toObservation(candles, buyIndex - 1),
            buyDay = buyDay.toObservation(candles, buyIndex),
            setupVolumeRising = setupDayTwo.volume > setupDayOne.volume,
            setupMoveRising = setupDayTwoChange > setupDayOneChange,
            entryPrice = roundTo2(entryPrice),
            targetPrice = roundTo2(targetPrice),
            outcome = if (targetIndex != null) TwoDayGreenCandleOutcomes.TARGET_HIT else TwoDayGreenCandleOutcomes.UNRESOLVED,
            exitDate = targetIndex?.let { index -> candles[index].candleDate.toString() },
            exitPrice = targetIndex?.let { roundTo2(targetPrice) },
            holdingTradingDays = targetIndex?.let { index -> index - buyIndex + 1 },
            maximumHighSinceEntryPct = roundTo2(maximumHighSinceEntryPct),
            unresolvedCloseReturnPct = targetIndex?.let { null } ?: roundTo2(((candles[lastTestIndex].close - entryPrice) / entryPrice) * 100.0),
        )
    }

    private fun isQualifyingSetup(candle: DailyCandle, dailyChangePct: Double): Boolean =
        candle.close > candle.open && dailyChangePct > MINIMUM_DAILY_CHANGE_PCT

    private fun dailyChangePct(candles: List<DailyCandle>, index: Int): Double? {
        val previousClose = candles.getOrNull(index - 1)?.close ?: return null
        if (previousClose <= 0.0) return null
        return ((candles[index].close - previousClose) / previousClose) * 100.0
    }

    private fun DailyCandle.toObservation(candles: List<DailyCandle>, index: Int): TwoDayGreenCandleObservation =
        TwoDayGreenCandleObservation(
            date = candleDate.toString(),
            open = roundTo2(open),
            high = roundTo2(high),
            low = roundTo2(low),
            close = roundTo2(close),
            volume = volume,
            dailyChangePct = dailyChangePct(candles, index)?.let(::roundTo2),
            greenDay = close > open,
            openToClosePct = open.takeIf { value -> value > 0.0 }?.let { value -> roundTo2(((close - value) / value) * 100.0) },
            lowToHighPct = low.takeIf { value -> value > 0.0 }?.let { value -> roundTo2(((high - value) / value) * 100.0) },
            closeLocationPct = if (high > low) roundTo2(((close - low) / (high - low)) * 100.0) else null,
        )

    private fun summarize(trades: List<TwoDayGreenCandleBacktestTrade>): TwoDayGreenCandleBacktestSummary {
        val targetHitCount = trades.count { trade -> trade.outcome == TwoDayGreenCandleOutcomes.TARGET_HIT }
        val holdingDays = trades.mapNotNull(TwoDayGreenCandleBacktestTrade::holdingTradingDays)
        return TwoDayGreenCandleBacktestSummary(
            setupCount = trades.size,
            targetHitCount = targetHitCount,
            unresolvedCount = trades.size - targetHitCount,
            targetHitRatePct = trades.takeIf { it.isNotEmpty() }?.let { roundTo2(targetHitCount * 100.0 / it.size) },
            averageHoldingTradingDays = holdingDays.takeIf { it.isNotEmpty() }?.average()?.let(::roundTo2),
        )
    }

    private fun roundTo2(value: Double): Double = round(value * 100.0) / 100.0

    private companion object {
        const val TEST_SESSION_COUNT = 40
        const val MINIMUM_CANDLE_COUNT = 3
        const val MINIMUM_DAILY_CHANGE_PCT = 1.0
        const val TARGET_PCT = 5.0
    }
}
