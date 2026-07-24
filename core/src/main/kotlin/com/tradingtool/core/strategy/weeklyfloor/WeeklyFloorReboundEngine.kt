package com.tradingtool.core.strategy.weeklyfloor

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

class WeeklyFloorReboundEngine {
    fun run(
        symbol: String,
        candles: List<DailyCandle>,
        floor: Double,
        ceiling: Double,
        activeFrom: LocalDate,
    ): WeeklyFloorReboundReport {
        val sorted = candles.sortedBy(DailyCandle::candleDate)
        require(sorted.isNotEmpty()) { "No daily candle data available for $symbol." }
        val startIndex = sorted.indexOfFirst { !it.candleDate.isBefore(activeFrom) }
        require(startIndex >= 0) { "No daily candles available on or after $activeFrom." }

        val activeCandles = sorted.drop(startIndex)
        val trades = mutableListOf<WeeklyFloorReboundRow>()
        val tradeWindows = mutableListOf<TradeWindow>()
        var index = startIndex

        while (index < sorted.size) {
            val candle = sorted[index]
            val trigger = reboundTrigger(candle, floor, ceiling)
            if (trigger == null || candle.high < trigger) {
                index += 1
                continue
            }

            val target = trigger * TARGET_MULTIPLIER
            val exitIndex = (index..sorted.lastIndex).firstOrNull { sorted[it].high >= target }
            val exit = exitIndex?.let(sorted::get)
            trades += WeeklyFloorReboundRow(
                zoneId = trades.size + 1,
                zoneCreatedDate = activeFrom.toString(),
                zoneFloor = floor,
                zoneCeiling = ceiling,
                outcome = if (exit == null) "OPEN" else "TARGET_HIT",
                testDate = candle.candleDate.toString(),
                testLow = candle.low,
                entryDate = candle.candleDate.toString(),
                entryPrice = trigger,
                stopPrice = null,
                targetPrice = target,
                exitDate = exit?.candleDate?.toString(),
                exitPrice = exit?.let { target },
                holdingTradingDays = exitIndex?.minus(index),
                returnPct = exit?.let { TARGET_RETURN_PCT },
                gapStop = false,
                exitWasAmbiguous = false,
            )
            tradeWindows += TradeWindow(index - startIndex, exitIndex?.minus(startIndex))
            index = (exitIndex ?: sorted.lastIndex) + 1
        }

        val daily = activeCandles.mapIndexed { activeIndex, candle ->
            val rawRow = dailyRow(candle, floor, ceiling)
            rawRow.copy(
                decision = auditDecision(
                    originalDecision = rawRow.decision,
                    activeIndex = activeIndex,
                    tradeWindows = tradeWindows,
                ),
            )
        }

        return WeeklyFloorReboundReport(
            symbol = symbol,
            testedFromDate = activeFrom.toString(),
            testedToDate = sorted.last().candleDate.toString(),
            summary = WeeklyFloorReboundSummary(
                zonesCreated = trades.size,
                filledTrades = trades.size,
                targetHitCount = trades.count { it.outcome == "TARGET_HIT" },
                stopLossCount = 0,
                fridayExitCount = 0,
            ),
            trades = trades,
            dailyData = daily,
        )
    }

    private fun dailyRow(candle: DailyCandle, floor: Double, ceiling: Double): WeeklyFloorReboundDailyRow {
        val trigger = reboundTrigger(candle, floor, ceiling)
        val target = trigger?.times(TARGET_MULTIPLIER)
        val decision = when {
            trigger == null -> "LOW_OUTSIDE_MANUAL_ZONE"
            candle.high < trigger -> "REBOUND_NOT_REACHED"
            candle.high >= requireNotNull(target) -> "ENTRY_AND_TARGET_SAME_DAY"
            else -> "ENTRY_TRIGGERED"
        }
        return WeeklyFloorReboundDailyRow(
            date = candle.candleDate.toString(),
            low = candle.low,
            high = candle.high,
            baseFloor = floor,
            baseCeiling = ceiling,
            baseWidthPct = ((ceiling / floor) - 1) * 100,
            reboundTrigger = trigger,
            targetPrice = target,
            decision = decision,
        )
    }

    private fun reboundTrigger(candle: DailyCandle, floor: Double, ceiling: Double): Double? =
        candle.low.takeIf { it in floor..ceiling }?.times(REBOUND_MULTIPLIER)

    private fun auditDecision(
        originalDecision: String,
        activeIndex: Int,
        tradeWindows: List<TradeWindow>,
    ): String {
        val position = tradeWindows.firstOrNull { window ->
            activeIndex > window.entryIndex && (window.exitIndex == null || activeIndex <= window.exitIndex)
        } ?: return originalDecision
        return if (activeIndex == position.exitIndex) "TARGET_HIT" else "POSITION_OPEN_WAITING_FOR_TARGET"
    }

    private data class TradeWindow(val entryIndex: Int, val exitIndex: Int?)

    private companion object {
        const val REBOUND_MULTIPLIER = 1.01
        const val TARGET_MULTIPLIER = 1.05
        const val TARGET_RETURN_PCT = 5.0
    }
}
