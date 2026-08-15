package com.tradingtool.core.strategy.baseretestbacktest

import com.tradingtool.core.candle.DailyCandle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class BaseRetestBacktestEngine {
    internal fun run(
        member: BaseRetestMember,
        candles: List<DailyCandle>,
        testFrom: LocalDate,
        toDate: LocalDate,
        targetPct: Double,
        stopLossPct: Double,
    ): List<BaseRetestObservation> {
        val sortedCandles = candles
            .filter { candle -> candle.candleDate in testFrom..toDate }
            .sortedBy(DailyCandle::candleDate)
        if (sortedCandles.size < MINIMUM_SETUP_SESSIONS) return emptyList()

        return findSetups(sortedCandles).mapNotNull { setup ->
            evaluateSetup(member, sortedCandles, setup, targetPct, stopLossPct)
        }
    }

    private fun findSetups(candles: List<DailyCandle>): List<Setup> {
        val setups = mutableListOf<Setup>()
        var searchStartIndex = 0
        while (searchStartIndex < candles.lastIndex) {
            val setup = findNextSetup(candles, searchStartIndex) ?: break
            setups.add(setup)
            searchStartIndex = setup.confirmationIndex + 1
        }
        return setups
    }

    private fun findNextSetup(candles: List<DailyCandle>, startIndex: Int): Setup? {
        var earliestSetup: Setup? = null
        for (firstLowIndex in startIndex until candles.lastIndex) {
            if (earliestSetup != null && firstLowIndex >= earliestSetup.confirmationIndex) break
            val candidate = findSetupFromFirstLow(candles, firstLowIndex) ?: continue
            if (earliestSetup == null || candidate.confirmationIndex < earliestSetup.confirmationIndex) {
                earliestSetup = candidate
            }
        }
        return earliestSetup
    }

    private fun findSetupFromFirstLow(candles: List<DailyCandle>, firstLowIndex: Int): Setup? {
        val firstReboundIndex = findFirstRebound(candles, firstLowIndex) ?: return null
        val firstLow = candles[firstLowIndex]

        for (secondLowIndex in firstReboundIndex + 1 until candles.size) {
            val secondLow = candles[secondLowIndex]
            if (secondLow.low < firstLow.low * (1.0 - LOW_TOLERANCE_PCT / 100.0) - COMPARISON_TOLERANCE) break
            if (differencePct(secondLow.low, firstLow.low) > LOW_TOLERANCE_PCT + COMPARISON_TOLERANCE) continue

            val confirmationIndex = findConfirmation(candles, firstLowIndex, secondLowIndex) ?: continue
            return Setup(firstLowIndex, firstReboundIndex, secondLowIndex, confirmationIndex)
        }
        return null
    }

    private fun findFirstRebound(candles: List<DailyCandle>, firstLowIndex: Int): Int? {
        val firstLow = candles[firstLowIndex]
        for (index in firstLowIndex + 1 until candles.size) {
            val candle = candles[index]
            if (candle.low < firstLow.low - COMPARISON_TOLERANCE) return null
            if (movePct(candle.high, firstLow.low) + COMPARISON_TOLERANCE >= REBOUND_PCT) return index
        }
        return null
    }

    private fun findConfirmation(
        candles: List<DailyCandle>,
        firstLowIndex: Int,
        secondLowIndex: Int,
    ): Int? {
        val firstLow = candles[firstLowIndex]
        val secondLow = candles[secondLowIndex]
        val basePrice = minOf(firstLow.low, secondLow.low)

        for (confirmationIndex in secondLowIndex until candles.size) {
            val candle = candles[confirmationIndex]
            if (candle.low < basePrice * (1.0 - LOW_TOLERANCE_PCT / 100.0) - COMPARISON_TOLERANCE) {
                return null
            }
            val reachesConfirmation = movePct(candle.high, secondLow.low) + COMPARISON_TOLERANCE >= REBOUND_PCT
            val sequenceIsKnown = confirmationIndex > secondLowIndex || candle.close > candle.open
            if (reachesConfirmation && sequenceIsKnown && confirmationIndex < candles.lastIndex) {
                return confirmationIndex
            }
        }
        return null
    }

    private fun evaluateSetup(
        member: BaseRetestMember,
        candles: List<DailyCandle>,
        setup: Setup,
        targetPct: Double,
        stopLossPct: Double,
    ): BaseRetestObservation? {
        val firstLow = candles[setup.firstLowIndex]
        val secondLow = candles[setup.secondLowIndex]
        val orderStartIndex = setup.confirmationIndex + 1
        val orderStart = candles.getOrNull(orderStartIndex) ?: return null
        val basePrice = minOf(firstLow.low, secondLow.low)
        val limitPrice = roundTo2(basePrice * (1.0 + LIMIT_OFFSET_PCT / 100.0))
        val invalidationClosePrice = roundTo2(basePrice * (1.0 - INVALIDATION_PCT / 100.0))

        for (orderIndex in orderStartIndex until candles.size) {
            val candle = candles[orderIndex]
            if (candle.low <= limitPrice + COMPARISON_TOLERANCE) {
                val fillPrice = if (candle.open <= limitPrice) candle.open else limitPrice
                val trade = evaluateTrade(candles, orderIndex, fillPrice, targetPct, stopLossPct)
                return buildObservation(
                    member = member,
                    candles = candles,
                    setup = setup,
                    basePrice = basePrice,
                    limitPrice = limitPrice,
                    invalidationClosePrice = invalidationClosePrice,
                    orderStart = orderStart,
                    orderEnd = candles[trade.exitIndex],
                    invalidationDate = null,
                    trade = trade,
                    outcome = trade.outcome,
                )
            }
            if (candle.close < invalidationClosePrice - COMPARISON_TOLERANCE) {
                return buildObservation(
                    member = member,
                    candles = candles,
                    setup = setup,
                    basePrice = basePrice,
                    limitPrice = limitPrice,
                    invalidationClosePrice = invalidationClosePrice,
                    orderStart = orderStart,
                    orderEnd = candle,
                    invalidationDate = candle.candleDate,
                    trade = null,
                    outcome = BaseRetestOutcomes.BASE_INVALIDATED,
                )
            }
        }

        return buildObservation(
            member = member,
            candles = candles,
            setup = setup,
            basePrice = basePrice,
            limitPrice = limitPrice,
            invalidationClosePrice = invalidationClosePrice,
            orderStart = orderStart,
            orderEnd = candles.last(),
            invalidationDate = null,
            trade = null,
            outcome = BaseRetestOutcomes.NO_FILL,
        )
    }

    private fun evaluateTrade(
        candles: List<DailyCandle>,
        fillIndex: Int,
        fillPrice: Double,
        targetPct: Double,
        stopLossPct: Double,
    ): Trade {
        val targetPrice = roundTo2(fillPrice * (1.0 + targetPct / 100.0))
        val stopLossPrice = roundTo2(fillPrice * (1.0 - stopLossPct / 100.0))

        for (index in fillIndex until candles.size) {
            val candle = candles[index]
            if (candle.open <= stopLossPrice) {
                return Trade(fillIndex, index, fillPrice, candle.open, targetPrice, stopLossPrice, BaseRetestOutcomes.STOP_LOSS)
            }
            if (index > fillIndex && candle.open >= targetPrice) {
                return Trade(fillIndex, index, fillPrice, candle.open, targetPrice, stopLossPrice, BaseRetestOutcomes.TARGET_HIT)
            }
            if (candle.low <= stopLossPrice + COMPARISON_TOLERANCE) {
                return Trade(fillIndex, index, fillPrice, stopLossPrice, targetPrice, stopLossPrice, BaseRetestOutcomes.STOP_LOSS)
            }

            val targetCanFollowFill = index > fillIndex || candle.close > candle.open
            if (targetCanFollowFill && candle.high >= targetPrice - COMPARISON_TOLERANCE) {
                return Trade(fillIndex, index, fillPrice, targetPrice, targetPrice, stopLossPrice, BaseRetestOutcomes.TARGET_HIT)
            }
        }

        val lastIndex = candles.lastIndex
        return Trade(
            fillIndex = fillIndex,
            exitIndex = lastIndex,
            fillPrice = fillPrice,
            exitPrice = candles.last().close,
            targetPrice = targetPrice,
            stopLossPrice = stopLossPrice,
            outcome = BaseRetestOutcomes.END_OF_DATA_EXIT,
        )
    }

    private fun buildObservation(
        member: BaseRetestMember,
        candles: List<DailyCandle>,
        setup: Setup,
        basePrice: Double,
        limitPrice: Double,
        invalidationClosePrice: Double,
        orderStart: DailyCandle,
        orderEnd: DailyCandle,
        invalidationDate: LocalDate?,
        trade: Trade?,
        outcome: String,
    ): BaseRetestObservation {
        val firstLow = candles[setup.firstLowIndex]
        val firstRebound = candles[setup.firstReboundIndex]
        val secondLow = candles[setup.secondLowIndex]
        val confirmation = candles[setup.confirmationIndex]
        return BaseRetestObservation(
            symbol = member.symbol,
            companyName = member.companyName,
            instrumentToken = member.instrumentToken,
            firstLowDate = firstLow.candleDate.toString(),
            firstLow = roundTo2(firstLow.low),
            firstReboundDate = firstRebound.candleDate.toString(),
            firstReboundHigh = roundTo2(firstRebound.high),
            firstReboundMovePct = roundTo2(movePct(firstRebound.high, firstLow.low)),
            secondLowDate = secondLow.candleDate.toString(),
            secondLow = roundTo2(secondLow.low),
            lowDifferencePct = roundTo2(differencePct(secondLow.low, firstLow.low)),
            confirmationDate = confirmation.candleDate.toString(),
            confirmationHigh = roundTo2(confirmation.high),
            confirmationMovePct = roundTo2(movePct(confirmation.high, secondLow.low)),
            basePrice = roundTo2(basePrice),
            limitPrice = limitPrice,
            invalidationClosePrice = invalidationClosePrice,
            orderActiveDate = orderStart.candleDate.toString(),
            orderEndDate = orderEnd.candleDate.toString(),
            invalidationDate = invalidationDate?.toString(),
            fillDate = trade?.let { candles[it.fillIndex].candleDate.toString() },
            fillPrice = trade?.fillPrice?.let(::roundTo2),
            targetPrice = trade?.targetPrice,
            stopLossPrice = trade?.stopLossPrice,
            exitDate = trade?.let { candles[it.exitIndex].candleDate.toString() },
            exitPrice = trade?.exitPrice?.let(::roundTo2),
            outcome = outcome,
            pnlPct = trade?.let { roundTo2(movePct(it.exitPrice, it.fillPrice)) },
            holdingSessions = trade?.let { it.exitIndex - it.fillIndex + 1 },
        )
    }

    private fun movePct(value: Double, base: Double): Double = (value / base - 1.0) * 100.0

    private fun differencePct(value: Double, reference: Double): Double = kotlin.math.abs(value / reference - 1.0) * 100.0

    private fun roundTo2(value: Double): Double = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    private data class Setup(
        val firstLowIndex: Int,
        val firstReboundIndex: Int,
        val secondLowIndex: Int,
        val confirmationIndex: Int,
    )

    private data class Trade(
        val fillIndex: Int,
        val exitIndex: Int,
        val fillPrice: Double,
        val exitPrice: Double,
        val targetPrice: Double,
        val stopLossPrice: Double,
        val outcome: String,
    )

    private companion object {
        const val MINIMUM_SETUP_SESSIONS = 4
        const val LOW_TOLERANCE_PCT = 1.0
        const val REBOUND_PCT = 5.0
        const val LIMIT_OFFSET_PCT = 1.0
        const val INVALIDATION_PCT = 1.0
        const val COMPARISON_TOLERANCE = 1.0e-8
    }
}
