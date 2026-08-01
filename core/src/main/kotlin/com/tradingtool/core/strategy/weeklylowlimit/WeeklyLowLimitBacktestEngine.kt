package com.tradingtool.core.strategy.weeklylowlimit

import com.tradingtool.core.candle.DailyCandle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class WeeklyLowLimitBacktestEngine {
    fun run(
        symbol: String,
        companyName: String?,
        candles: List<DailyCandle>,
        testFrom: LocalDate,
        toDate: LocalDate,
        entryRule: String = WeeklyLowLimitBacktestEntryRules.ANY_DAY_MAX_5_TRADING_DAYS,
    ): WeeklyLowLimitBacktestSymbolReport {
        require(entryRule in WeeklyLowLimitBacktestEntryRules.all) { "Unsupported weekly low limit entry rule: $entryRule" }

        val sortedCandles = candles.sortedBy(DailyCandle::candleDate)
        require(sortedCandles.isNotEmpty()) { "No daily candle data is available for $symbol." }

        val currentWeekStart = toDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val completedCandles = sortedCandles.filter { candle ->
            candle.candleDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) < currentWeekStart
        }
        val weeks = completedCandles
            .groupBy { candle -> candle.candleDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .mapValues { (_, weekCandles) -> weekCandles.sortedBy(DailyCandle::candleDate) }
        val firstEntryWeek = testFrom.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        val completedWeekStarts = weeks.keys
            .filter { weekStart -> weekStart >= firstEntryWeek && weeks.containsKey(weekStart.minusWeeks(1)) }
            .sorted()
        val candleIndexByDate = completedCandles.mapIndexed { index, candle -> candle.candleDate to index }.toMap()
        val instrumentToken = sortedCandles.first().instrumentToken
        var activeThroughIndex = -1
        val trades = completedWeekStarts.map { weekStart ->
            val previousWeekStart = weekStart.minusWeeks(1)
            val previousWeek = requireNotNull(weeks[previousWeekStart])
            val entryWeek = requireNotNull(weeks[weekStart])
            val entryWeekIndex = requireNotNull(candleIndexByDate[entryWeek.first().candleDate])
            if (entryRule == WeeklyLowLimitBacktestEntryRules.ANY_DAY_MAX_5_TRADING_DAYS && entryWeekIndex <= activeThroughIndex) {
                buildSkippedTrade(
                    symbol = symbol,
                    instrumentToken = instrumentToken,
                    previousWeekStart = previousWeekStart,
                    previousWeek = previousWeek,
                    entryWeekStart = weekStart,
                    entryWeek = entryWeek,
                    entryRule = entryRule,
                )
            } else {
                val builtTrade = buildTrade(
                    symbol = symbol,
                    instrumentToken = instrumentToken,
                    previousWeekStart = previousWeekStart,
                    previousWeek = previousWeek,
                    entryWeekStart = weekStart,
                    entryWeek = entryWeek,
                    completedCandles = completedCandles,
                    candleIndexByDate = candleIndexByDate,
                    entryRule = entryRule,
                    entryWeekIndex = entryWeekIndex,
                )
                if (builtTrade.exitIndex != null) {
                    activeThroughIndex = builtTrade.exitIndex
                }
                builtTrade.trade
            }
        }

        val reportFrom = testFrom.toString()
        val reportTo = completedWeekStarts
            .lastOrNull()
            ?.let { weekStart -> weeks.getValue(weekStart).last().candleDate.toString() }
            ?: sortedCandles.last().candleDate.toString()
        return WeeklyLowLimitBacktestSymbolReport(
            symbol = symbol,
            companyName = companyName,
            entryRule = entryRule,
            testedFromDate = reportFrom,
            testedToDate = reportTo,
            summary = summarizeWeeklyLowLimitTrades(trades),
            trades = trades,
        )
    }

    private fun buildTrade(
        symbol: String,
        instrumentToken: Long,
        previousWeekStart: LocalDate,
        previousWeek: List<DailyCandle>,
        entryWeekStart: LocalDate,
        entryWeek: List<DailyCandle>,
        completedCandles: List<DailyCandle>,
        candleIndexByDate: Map<LocalDate, Int>,
        entryRule: String,
        entryWeekIndex: Int,
    ): BuiltTrade {
        val previousWeekLowCandle = previousWeek.minBy(DailyCandle::low)
        val previousWeekLow = previousWeekLowCandle.low
        val previousWeekLastClose = previousWeek.last().close
        val limitPrice = previousWeekLow * LIMIT_OFFSET_MULTIPLIER
        val eligibleEntryDays = entryWeek.filter { candle ->
            entryRule == WeeklyLowLimitBacktestEntryRules.ANY_DAY_MAX_5_TRADING_DAYS ||
                candle.candleDate.dayOfWeek in FIRST_THREE_DAYS
        }
        val orderStartDate = entryWeek.first().candleDate
        val orderEndDate = eligibleEntryDays.lastOrNull()?.candleDate ?: entryWeek.first().candleDate
        if (previousWeekLastClose < limitPrice) {
            return BuiltTrade(
                trade = baseTrade(
                    symbol = symbol,
                    instrumentToken = instrumentToken,
                    previousWeekStart = previousWeekStart,
                    entryWeekStart = entryWeekStart,
                    orderStartDate = orderStartDate,
                    orderEndDate = orderEndDate,
                    previousWeekLow = previousWeekLow,
                    previousWeekLowDate = previousWeekLowCandle.candleDate,
                    previousWeekLastClose = previousWeekLastClose,
                    limitPrice = limitPrice,
                    outcome = PREMARKET_FILTER_SKIP,
                ),
                exitIndex = null,
            )
        }
        val entryCandle = eligibleEntryDays.firstOrNull { candle -> candle.low <= limitPrice }
        if (entryCandle == null) {
            return BuiltTrade(
                trade = baseTrade(
                    symbol = symbol,
                    instrumentToken = instrumentToken,
                    previousWeekStart = previousWeekStart,
                    entryWeekStart = entryWeekStart,
                    orderStartDate = orderStartDate,
                    orderEndDate = orderEndDate,
                    previousWeekLow = previousWeekLow,
                    previousWeekLowDate = previousWeekLowCandle.candleDate,
                    previousWeekLastClose = previousWeekLastClose,
                    limitPrice = limitPrice,
                    outcome = NO_FILL,
                ),
                exitIndex = null,
            )
        }

        val entryOpenDeviationPct = kotlin.math.abs((entryCandle.open / limitPrice) - 1.0) * 100.0
        if (entryOpenDeviationPct > MAX_OPEN_DEVIATION_PCT) {
            return BuiltTrade(
                trade = baseTrade(
                    symbol = symbol,
                    instrumentToken = instrumentToken,
                    previousWeekStart = previousWeekStart,
                    entryWeekStart = entryWeekStart,
                    orderStartDate = orderStartDate,
                    orderEndDate = orderEndDate,
                    previousWeekLow = previousWeekLow,
                    previousWeekLowDate = previousWeekLowCandle.candleDate,
                    previousWeekLastClose = previousWeekLastClose,
                    limitPrice = limitPrice,
                    outcome = OPEN_DEVIATION_SKIP,
                    entryOpenDeviationPct = entryOpenDeviationPct,
                ),
                exitIndex = null,
            )
        }

        val entryIndex = requireNotNull(candleIndexByDate[entryCandle.candleDate])
        val entryPrice = if (entryCandle.open < limitPrice) entryCandle.open else limitPrice
        val stopPrice = entryPrice * STOP_MULTIPLIER
        val targetPrice = entryPrice * TARGET_MULTIPLIER
        val exit = if (entryRule == WeeklyLowLimitBacktestEntryRules.ANY_DAY_MAX_5_TRADING_DAYS) {
            val maxExitIndex = minOf(entryIndex + MAX_HOLDING_TRADING_DAYS, completedCandles.lastIndex)
            findExit(completedCandles, entryIndex, maxExitIndex, stopPrice, targetPrice)
                ?: Exit(
                    date = completedCandles[maxExitIndex].candleDate,
                    price = completedCandles[maxExitIndex].close,
                    outcome = TIME_EXIT,
                    candleIndex = maxExitIndex,
                    ambiguous = false,
                )
        } else {
            val entryWeekLastIndex = entryWeekIndex + entryWeek.lastIndex
            findExit(completedCandles, entryIndex, entryWeekLastIndex, stopPrice, targetPrice)
                ?: Exit(
                    date = entryWeek.last().candleDate,
                    price = entryWeek.last().close,
                    outcome = TIME_EXIT,
                    candleIndex = entryWeekLastIndex,
                    ambiguous = false,
                )
        }
        return BuiltTrade(
            trade = baseTrade(
                symbol = symbol,
                instrumentToken = instrumentToken,
                previousWeekStart = previousWeekStart,
                entryWeekStart = entryWeekStart,
                orderStartDate = orderStartDate,
                orderEndDate = orderEndDate,
                previousWeekLow = previousWeekLow,
                previousWeekLowDate = previousWeekLowCandle.candleDate,
                previousWeekLastClose = previousWeekLastClose,
                limitPrice = limitPrice,
                outcome = exit.outcome,
                entryDate = entryCandle.candleDate,
                entryOpenDeviationPct = entryOpenDeviationPct,
                entryPrice = entryPrice,
                stopPrice = stopPrice,
                targetPrice = targetPrice,
                exitDate = exit.date,
                exitPrice = exit.price,
                holdingTradingDays = exit.candleIndex - entryIndex,
                returnPct = ((exit.price / entryPrice) - 1.0) * 100.0,
                gapFill = entryCandle.open < limitPrice,
                exitWasAmbiguous = exit.ambiguous,
            ),
            exitIndex = exit.candleIndex,
        )
    }

    private fun buildSkippedTrade(
        symbol: String,
        instrumentToken: Long,
        previousWeekStart: LocalDate,
        previousWeek: List<DailyCandle>,
        entryWeekStart: LocalDate,
        entryWeek: List<DailyCandle>,
        entryRule: String,
    ): WeeklyLowLimitBacktestTrade {
        val previousWeekLowCandle = previousWeek.minBy(DailyCandle::low)
        val previousWeekLow = previousWeekLowCandle.low
        val previousWeekLastClose = previousWeek.last().close
        val limitPrice = previousWeekLow * LIMIT_OFFSET_MULTIPLIER
        return baseTrade(
            symbol = symbol,
            instrumentToken = instrumentToken,
            previousWeekStart = previousWeekStart,
            entryWeekStart = entryWeekStart,
            orderStartDate = entryWeek.first().candleDate,
            orderEndDate = entryWeek.lastOrNull { candle ->
                entryRule == WeeklyLowLimitBacktestEntryRules.ANY_DAY_MAX_5_TRADING_DAYS || candle.candleDate.dayOfWeek in FIRST_THREE_DAYS
            }?.candleDate ?: entryWeek.first().candleDate,
            previousWeekLow = previousWeekLow,
            previousWeekLowDate = previousWeekLowCandle.candleDate,
            previousWeekLastClose = previousWeekLastClose,
            limitPrice = limitPrice,
            outcome = POSITION_OPEN_SKIP,
        )
    }

    private fun findExit(
        candles: List<DailyCandle>,
        entryIndex: Int,
        lastExitIndex: Int,
        stopPrice: Double,
        targetPrice: Double,
    ): Exit? {
        for (index in (entryIndex + 1)..lastExitIndex) {
            val candle = candles[index]
            if (candle.open <= stopPrice) {
                return Exit(candle.candleDate, candle.open, STOP_LOSS, index, ambiguous = false)
            }
            if (candle.open >= targetPrice) {
                return Exit(candle.candleDate, targetPrice, TARGET_HIT, index, ambiguous = false)
            }
            if (candle.low <= stopPrice && candle.high >= targetPrice) {
                return Exit(candle.candleDate, stopPrice, STOP_LOSS, index, ambiguous = true)
            }
            if (candle.low <= stopPrice) {
                return Exit(candle.candleDate, stopPrice, STOP_LOSS, index, ambiguous = false)
            }
            if (candle.high >= targetPrice) {
                return Exit(candle.candleDate, targetPrice, TARGET_HIT, index, ambiguous = false)
            }
        }
        return null
    }

    private fun baseTrade(
        symbol: String,
        instrumentToken: Long,
        previousWeekStart: LocalDate,
        entryWeekStart: LocalDate,
        orderStartDate: LocalDate,
        orderEndDate: LocalDate,
        previousWeekLow: Double,
        previousWeekLowDate: LocalDate,
        previousWeekLastClose: Double,
        limitPrice: Double,
        outcome: String,
        entryDate: LocalDate? = null,
        entryOpenDeviationPct: Double? = null,
        entryPrice: Double? = null,
        stopPrice: Double? = null,
        targetPrice: Double? = null,
        exitDate: LocalDate? = null,
        exitPrice: Double? = null,
        holdingTradingDays: Int? = null,
        returnPct: Double? = null,
        gapFill: Boolean = false,
        exitWasAmbiguous: Boolean = false,
    ): WeeklyLowLimitBacktestTrade = WeeklyLowLimitBacktestTrade(
        symbol = symbol,
        instrumentToken = instrumentToken,
        previousWeekStartDate = previousWeekStart.toString(),
        entryWeekStartDate = entryWeekStart.toString(),
        orderStartDate = orderStartDate.toString(),
        orderEndDate = orderEndDate.toString(),
        previousWeekLow = previousWeekLow,
        previousWeekLowDate = previousWeekLowDate.toString(),
        previousWeekLastClose = previousWeekLastClose,
        limitPrice = limitPrice,
        outcome = outcome,
        entryDate = entryDate?.toString(),
        entryOpenDeviationPct = entryOpenDeviationPct,
        entryPrice = entryPrice,
        stopPrice = stopPrice,
        targetPrice = targetPrice,
        exitDate = exitDate?.toString(),
        exitPrice = exitPrice,
        holdingTradingDays = holdingTradingDays,
        returnPct = returnPct,
        gapFill = gapFill,
        exitWasAmbiguous = exitWasAmbiguous,
    )

    private data class BuiltTrade(
        val trade: WeeklyLowLimitBacktestTrade,
        val exitIndex: Int?,
    )

    private data class Exit(
        val date: LocalDate,
        val price: Double,
        val outcome: String,
        val candleIndex: Int,
        val ambiguous: Boolean,
    )

    private companion object {
        val FIRST_THREE_DAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)
        const val MAX_HOLDING_TRADING_DAYS = 5
        const val LIMIT_OFFSET_MULTIPLIER = 1.01
        const val TARGET_MULTIPLIER = 1.05
        const val STOP_MULTIPLIER = 0.95
        const val NO_FILL = "NO_FILL"
        const val POSITION_OPEN_SKIP = "POSITION_OPEN_SKIP"
        const val PREMARKET_FILTER_SKIP = "PREMARKET_FILTER_SKIP"
        const val OPEN_DEVIATION_SKIP = "OPEN_DEVIATION_SKIP"
        const val MAX_OPEN_DEVIATION_PCT = 1.0
        const val TARGET_HIT = "TARGET_HIT"
        const val STOP_LOSS = "STOP_LOSS"
        const val TIME_EXIT = "TIME_EXIT"
    }
}
