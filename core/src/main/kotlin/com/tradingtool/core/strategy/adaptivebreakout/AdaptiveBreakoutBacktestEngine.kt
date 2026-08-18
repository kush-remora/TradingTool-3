package com.tradingtool.core.strategy.adaptivebreakout

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

internal object AdaptiveBreakoutBacktestEngine {
    fun run(
        candles: List<DailyCandle>,
        testFromDate: LocalDate,
        testToDate: LocalDate,
        targetPct: Double,
        stopLossPct: Double,
    ): AdaptiveBreakoutBacktestResponse {
        require(targetPct > 0.0) { "targetPct must be positive." }
        require(stopLossPct > 0.0) { "stopLossPct must be positive." }
        require(!testFromDate.isAfter(testToDate)) { "testFromDate must not be after testToDate." }

        val orderedCandles = candles.distinctBy(DailyCandle::candleDate).sortedBy(DailyCandle::candleDate)
        val evaluation = AdaptiveBreakoutEngine.evaluate(orderedCandles)
            ?: throw IllegalArgumentException("Not enough candle history to evaluate adaptive breakouts.")
        val breakoutSteps = evaluation.rawSteps
            .withIndex()
            .filter { (_, step) ->
                step.decision == AdaptiveBreakoutDecision.FRESH_BREAKOUT &&
                    step.date >= testFromDate.toString() && step.date <= testToDate.toString()
            }

        val trades = mutableListOf<AdaptiveBreakoutBacktestTrade>()
        var nextEntryIndex = 0
        breakoutSteps.forEach { indexedStep ->
            val entryIndex = orderedCandles.indices.firstOrNull { index ->
                index > indexedStep.index && index >= nextEntryIndex && orderedCandles[index].hasTradableRange()
            } ?: return@forEach
            val entryCandle = orderedCandles[entryIndex]
            if (entryCandle.candleDate.isAfter(testToDate)) return@forEach

            val trade = simulateTrade(
                candles = orderedCandles,
                breakoutStep = indexedStep.value,
                entryIndex = entryIndex,
                testToDate = testToDate,
                targetPct = targetPct,
                stopLossPct = stopLossPct,
            )
            trades += trade
            nextEntryIndex = tradeExitIndex(orderedCandles, trade) + 1
        }

        val targetHitCount = trades.count { trade -> trade.exitReason == AdaptiveBreakoutBacktestExitReason.TARGET_HIT }
        val stopLossCount = trades.count { trade ->
            trade.exitReason == AdaptiveBreakoutBacktestExitReason.STOP_LOSS ||
                trade.exitReason == AdaptiveBreakoutBacktestExitReason.STOP_LOSS_SAME_CANDLE
        }
        val endOfTestCount = trades.count { trade -> trade.exitReason == AdaptiveBreakoutBacktestExitReason.END_OF_TEST }
        val completedTradeCount = targetHitCount + stopLossCount

        return AdaptiveBreakoutBacktestResponse(
            symbol = orderedCandles.first().symbol,
            testedFromDate = testFromDate.toString(),
            testedToDate = testToDate.toString(),
            targetPct = targetPct,
            stopLossPct = stopLossPct,
            entryRule = "Fresh breakout is known after its completed close; enter at the first later session open with a tradable range. Locked zero-range sessions are skipped.",
            ambiguousCandleRule = "If one daily candle touches both target and stop, stop is assumed first because daily OHLC has no intraday order.",
            summary = AdaptiveBreakoutBacktestSummary(
                freshBreakoutCount = breakoutSteps.size,
                enteredTradeCount = trades.size,
                targetHitCount = targetHitCount,
                stopLossCount = stopLossCount,
                endOfTestCount = endOfTestCount,
                winRatePct = if (completedTradeCount == 0) null else targetHitCount * 100.0 / completedTradeCount,
                averageHoldingSessions = trades.map { trade -> trade.holdingSessions }.averageOrNull(),
            ),
            trades = trades,
        )
    }

    private fun simulateTrade(
        candles: List<DailyCandle>,
        breakoutStep: AdaptiveBreakoutRawStep,
        entryIndex: Int,
        testToDate: LocalDate,
        targetPct: Double,
        stopLossPct: Double,
    ): AdaptiveBreakoutBacktestTrade {
        val entryCandle = candles[entryIndex]
        val entryPrice = entryCandle.open
        val targetPrice = entryPrice * (1.0 + targetPct / 100.0)
        val stopPrice = entryPrice * (1.0 - stopLossPct / 100.0)
        val finalIndex = candles.indexOfLast { candle -> !candle.candleDate.isAfter(testToDate) }
        val exitIndex = (entryIndex..finalIndex).firstOrNull { index ->
            val candle = candles[index]
            candle.high >= targetPrice || candle.low <= stopPrice
        }

        if (exitIndex == null) {
            val lastCandle = candles[finalIndex]
            return buildTrade(
                breakoutStep = breakoutStep,
                entryCandle = entryCandle,
                targetPrice = targetPrice,
                stopPrice = stopPrice,
                exitCandle = lastCandle,
                exitPrice = lastCandle.close,
                exitReason = AdaptiveBreakoutBacktestExitReason.END_OF_TEST,
                holdingSessions = finalIndex - entryIndex + 1,
                ambiguousSameCandle = false,
            )
        }

        val exitCandle = candles[exitIndex]
        val targetTouched = exitCandle.high >= targetPrice
        val stopTouched = exitCandle.low <= stopPrice
        val sameCandle = targetTouched && stopTouched
        val exitReason = when {
            sameCandle -> AdaptiveBreakoutBacktestExitReason.STOP_LOSS_SAME_CANDLE
            targetTouched -> AdaptiveBreakoutBacktestExitReason.TARGET_HIT
            else -> AdaptiveBreakoutBacktestExitReason.STOP_LOSS
        }
        val exitPrice = if (exitReason == AdaptiveBreakoutBacktestExitReason.TARGET_HIT) targetPrice else stopPrice
        return buildTrade(
            breakoutStep = breakoutStep,
            entryCandle = entryCandle,
            targetPrice = targetPrice,
            stopPrice = stopPrice,
            exitCandle = exitCandle,
            exitPrice = exitPrice,
            exitReason = exitReason,
            holdingSessions = exitIndex - entryIndex + 1,
            ambiguousSameCandle = sameCandle,
        )
    }

    private fun buildTrade(
        breakoutStep: AdaptiveBreakoutRawStep,
        entryCandle: DailyCandle,
        targetPrice: Double,
        stopPrice: Double,
        exitCandle: DailyCandle,
        exitPrice: Double,
        exitReason: AdaptiveBreakoutBacktestExitReason,
        holdingSessions: Int,
        ambiguousSameCandle: Boolean,
    ): AdaptiveBreakoutBacktestTrade = AdaptiveBreakoutBacktestTrade(
        symbol = entryCandle.symbol,
        breakoutDate = breakoutStep.date,
        breakoutClose = breakoutStep.close,
        entryDate = entryCandle.candleDate.toString(),
        entryPrice = entryCandle.open,
        targetPrice = targetPrice,
        stopPrice = stopPrice,
        exitDate = exitCandle.candleDate.toString(),
        exitPrice = exitPrice,
        exitReason = exitReason,
        holdingSessions = holdingSessions,
        returnPct = (exitPrice / entryCandle.open - 1.0) * 100.0,
        ambiguousSameCandle = ambiguousSameCandle,
    )

    private fun tradeExitIndex(candles: List<DailyCandle>, trade: AdaptiveBreakoutBacktestTrade): Int =
        candles.indexOfFirst { candle -> candle.candleDate.toString() == trade.exitDate }

    private fun List<Int>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private fun DailyCandle.hasTradableRange(): Boolean = high > low
}
