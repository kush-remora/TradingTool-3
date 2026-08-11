package com.tradingtool.core.strategy.fiftytwomomentum

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

internal object FiftyTwoWeekMomentumRule5BacktestEngine {
    fun evaluate(
        symbol: String,
        companyName: String,
        candles: List<DailyCandle>,
        periodStartDate: LocalDate,
        requestedAsOfDate: LocalDate,
        breakoutPeriodSessions: Int,
        nearHighTolerancePct: Double = 0.0,
        targetPct: Double,
    ): Rule5BacktestSymbolEvaluation {
        val orderedCandles = candles
            .filter { candle -> !candle.candleDate.isAfter(requestedAsOfDate) }
            .distinctBy(DailyCandle::candleDate)
            .sortedBy(DailyCandle::candleDate)
        val candleIndexByDate = orderedCandles.mapIndexed { index, candle -> candle.candleDate to index }.toMap()
        val breakoutDays = FiftyTwoWeekMomentumRule5Engine.findFreshBreakouts(
            candles = orderedCandles,
            fromDate = periodStartDate,
            toDate = requestedAsOfDate,
            breakoutPeriodSessions = breakoutPeriodSessions,
            nearHighTolerancePct = nearHighTolerancePct,
        )

        val signals = mutableListOf<Rule5BacktestSignal>()
        val trades = mutableListOf<Rule5BacktestTrade>()
        var openUntilIndex: Int? = null

        breakoutDays.sortedBy(Rule5BreakoutDay::date).forEach { breakoutDay ->
            val signalIndex = candleIndexByDate[LocalDate.parse(breakoutDay.date)] ?: return@forEach
            val currentOpenUntilIndex = openUntilIndex
            if (currentOpenUntilIndex != null && signalIndex <= currentOpenUntilIndex) {
                signals += Rule5BacktestSignal(
                    symbol = symbol,
                    companyName = companyName,
                    signalDate = breakoutDay.date,
                    breakoutHigh = breakoutDay.high,
                    breakoutClose = breakoutDay.close,
                    referenceHigh = breakoutDay.referenceHigh,
                    referenceHighDaysAgo = breakoutDay.referenceHighDaysAgo,
                    closeVsReferenceHighPct = breakoutDay.closeVsReferenceHighPct,
                    outcome = SKIPPED_OPEN_POSITION,
                    entryPrice = null,
                    targetPrice = null,
                    tradeStatus = null,
                )
                return@forEach
            }

            val entryCandle = orderedCandles[signalIndex]
            val entryPrice = entryCandle.close
            val targetPrice = entryPrice * (1.0 + targetPct / 100.0)
            val latestPrice = orderedCandles.last().close
            val changeFromEntryPct = ((latestPrice - entryPrice) / entryPrice) * 100.0
            val exitIndex = ((signalIndex + 1)..orderedCandles.lastIndex)
                .firstOrNull { index -> orderedCandles[index].high >= targetPrice }
            val holdingTradingDays = (exitIndex ?: orderedCandles.lastIndex) - signalIndex
            val tradeStatus = if (exitIndex == null) OPEN else TARGET_HIT
            val trade = Rule5BacktestTrade(
                symbol = symbol,
                companyName = companyName,
                instrumentToken = entryCandle.instrumentToken,
                entryDate = entryCandle.candleDate.toString(),
                entryPrice = entryPrice,
                targetPrice = targetPrice,
                exitDate = exitIndex?.let { index -> orderedCandles[index].candleDate.toString() },
                exitPrice = exitIndex?.let { targetPrice },
                latestPrice = latestPrice,
                changeFromEntryPct = changeFromEntryPct,
                status = tradeStatus,
                holdingTradingDays = holdingTradingDays,
            )
            trades += trade
            signals += Rule5BacktestSignal(
                symbol = symbol,
                companyName = companyName,
                signalDate = breakoutDay.date,
                breakoutHigh = breakoutDay.high,
                breakoutClose = breakoutDay.close,
                referenceHigh = breakoutDay.referenceHigh,
                referenceHighDaysAgo = breakoutDay.referenceHighDaysAgo,
                closeVsReferenceHighPct = breakoutDay.closeVsReferenceHighPct,
                outcome = ENTERED,
                entryPrice = entryPrice,
                targetPrice = targetPrice,
                tradeStatus = tradeStatus,
            )
            openUntilIndex = exitIndex ?: orderedCandles.lastIndex
        }

        return Rule5BacktestSymbolEvaluation(signals = signals, trades = trades)
    }

    private const val ENTERED = "ENTERED"
    private const val SKIPPED_OPEN_POSITION = "SKIPPED_OPEN_POSITION"
    private const val TARGET_HIT = "TARGET_HIT"
    private const val OPEN = "OPEN"
}

internal data class Rule5BacktestSymbolEvaluation(
    val signals: List<Rule5BacktestSignal>,
    val trades: List<Rule5BacktestTrade>,
)
