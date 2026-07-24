package com.tradingtool.core.strategy.weeklybase

import com.tradingtool.core.candle.DailyCandle

class WeeklyBaseGroupBacktestEngine(
    private val baseEngine: WeeklyBaseDefinitionEngine = WeeklyBaseDefinitionEngine(),
) {
    fun run(symbol: String, candles: List<DailyCandle>, config: WeeklyBaseDefinitionConfig): SymbolBacktestResult {
        val baseReport = baseEngine.run(symbol, candles, config)
        val sortedCandles = candles.sortedBy(DailyCandle::candleDate)
        val trades = mutableListOf<WeeklyBaseGroupBacktestTrade>()
        var index = sortedCandles.indexOfFirst { candle -> candle.candleDate.toString() == baseReport.testedFromDate }.coerceAtLeast(0)

        val baseByDate = baseReport.rows.associateBy(WeeklyBaseDefinitionRow::evaluationDate)
        while (index < sortedCandles.size) {
            val candle = sortedCandles[index]
            val base = baseByDate[candle.candleDate.toString()]
            if (base == null || !base.isValid || candle.low !in base.zoneFloor..base.zoneCeiling) {
                index += 1
                continue
            }
            val entryPrice = candle.low * REBOUND_MULTIPLIER
            if (candle.high < entryPrice) {
                index += 1
                continue
            }
            val targetPrice = entryPrice * TARGET_MULTIPLIER
            val exitIndex = (index..sortedCandles.lastIndex).firstOrNull { exitCandle -> sortedCandles[exitCandle].high >= targetPrice }
            trades += WeeklyBaseGroupBacktestTrade(
                entryDate = candle.candleDate.toString(),
                entryPrice = entryPrice,
                targetPrice = targetPrice,
                exitDate = exitIndex?.let { sortedCandles[it].candleDate.toString() },
                outcome = if (exitIndex == null) "OPEN" else "TARGET_HIT",
                holdingTradingDays = exitIndex?.minus(index),
            )
            index = (exitIndex ?: sortedCandles.lastIndex) + 1
        }
        val latestValidBase = baseReport.rows.lastOrNull(WeeklyBaseDefinitionRow::isValid)
        return SymbolBacktestResult(baseReport, latestValidBase, trades)
    }

    data class SymbolBacktestResult(
        val baseReport: WeeklyBaseDefinitionReport,
        val latestValidBase: WeeklyBaseDefinitionRow?,
        val trades: List<WeeklyBaseGroupBacktestTrade>,
    )

    private companion object {
        const val REBOUND_MULTIPLIER = 1.01
        const val TARGET_MULTIPLIER = 1.05
    }
}
