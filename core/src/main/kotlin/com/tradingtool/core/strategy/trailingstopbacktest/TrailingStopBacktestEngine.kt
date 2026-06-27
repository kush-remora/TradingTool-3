package com.tradingtool.core.strategy.trailingstopbacktest

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.roundTo2
import com.tradingtool.core.technical.toTa4jSeries
import com.tradingtool.core.technical.calculateEma
import com.tradingtool.core.technical.getDoubleValue
import java.time.OffsetDateTime
import com.google.inject.Inject

class TrailingStopBacktestEngine @Inject constructor() {

    fun run(
        inputFile: String,
        priceDataToDate: java.time.LocalDate,
        allocationPerTrade: Double,
        contexts: List<TrailingStopSymbolContext>,
    ): TrailingStopBacktestReport {
        val tradeRows = contexts
            .map { context -> evaluateSignal(context, allocationPerTrade) }
            .sortedWith(
                compareBy<TrailingStopTradeRow> { it.signalDate }
                    .thenBy { it.symbol }
            )

        val aggregations = aggregateResults(tradeRows)

        return TrailingStopBacktestReport(
            generatedAt = OffsetDateTime.now().toString(),
            inputFile = inputFile,
            priceDataToDate = priceDataToDate.toString(),
            totalSignals = contexts.size,
            trades = tradeRows,
            aggregations = aggregations,
        )
    }

    private fun evaluateSignal(
        context: TrailingStopSymbolContext,
        allocationPerTrade: Double,
    ): TrailingStopTradeRow {
        val signal = context.signal
        val candles = context.candles.distinctBy { it.candleDate }.sortedBy { it.candleDate }
        
        val ta4jSeries = candles.toTa4jSeries()
        val ema20Indicator = ta4jSeries.calculateEma(20)
        
        val signalCandle = candles.find { it.candleDate == signal.signalDate }
        
        if (signalCandle == null) {
            return createEmptyRow(signal, "NO_SIGNAL_CANDLE_FOUND")
        }

        val entryIndex = candles.indexOfFirst { it.candleDate > signal.signalDate }
        if (entryIndex < 0) {
            return createEmptyRow(signal, "NO_NEXT_TRADING_DAY")
        }

        val entryCandle = candles[entryIndex]
        val entryPrice = entryCandle.open
        if (!entryPrice.isFinite() || entryPrice <= 0.0) {
            return createEmptyRow(signal, "INVALID_ENTRY_PRICE")
        }

        val shares = maxOf(1, (allocationPerTrade / entryPrice).toInt())
        val investedAmount = shares * entryPrice

        var exitPrice: Double? = null
        var exitIndex: Int? = null
        var outcome = "OPEN"

        for (i in entryIndex until candles.size) {
            val todayCandle = candles[i]
            val todayEma20 = ema20Indicator.getDoubleValue(i)
            
            // Check if we hit the stop loss (close < EMA20)
            if (todayEma20 > 0.0 && todayCandle.close < todayEma20) {
                exitPrice = todayCandle.close
                exitIndex = i
                outcome = "STOP_LOSS_EMA20"
                break
            }
        }

        // If it didn't exit, we mark it as OPEN and calculate MTM based on last available close
        if (exitPrice == null) {
            val lastCandle = candles.last()
            exitPrice = lastCandle.close
            exitIndex = candles.lastIndex
            outcome = "OPEN_MTM"
        }

        val exitValue = shares * exitPrice
        val profitLoss = exitValue - investedAmount
        val profitLossPct = (profitLoss / investedAmount) * 100.0
        val finalExitIndex = exitIndex ?: candles.lastIndex
        val holdingTradingDays = finalExitIndex - entryIndex

        return TrailingStopTradeRow(
            symbol = signal.symbol,
            marketCapName = signal.marketCapName,
            sector = signal.sector,
            signalDate = signal.signalDate.toString(),
            entryDate = entryCandle.candleDate.toString(),
            exitDate = candles[finalExitIndex].candleDate.toString(),
            entryPrice = entryPrice.roundTo2(),
            exitPrice = exitPrice.roundTo2(),
            shares = shares,
            investedAmount = investedAmount.roundTo2(),
            exitValue = exitValue.roundTo2(),
            profitLoss = profitLoss.roundTo2(),
            profitLossPct = profitLossPct.roundTo2(),
            holdingTradingDays = holdingTradingDays,
            outcome = outcome,
        )
    }

    private fun createEmptyRow(signal: TrailingStopSignal, outcome: String): TrailingStopTradeRow {
        return TrailingStopTradeRow(
            symbol = signal.symbol,
            marketCapName = signal.marketCapName,
            sector = signal.sector,
            signalDate = signal.signalDate.toString(),
            entryDate = null,
            exitDate = null,
            entryPrice = null,
            exitPrice = null,
            shares = 0,
            investedAmount = 0.0,
            exitValue = 0.0,
            profitLoss = 0.0,
            profitLossPct = null,
            holdingTradingDays = null,
            outcome = outcome,
        )
    }

    private fun aggregateResults(rows: List<TrailingStopTradeRow>): List<TrailingStopAggregateResult> {
        return rows
            .filter { it.entryDate != null }
            .groupBy { Pair(it.marketCapName, it.sector) }
            .map { (groupKey, groupRows) ->
                val (marketCapName, sector) = groupKey
                val totalInvested = groupRows.sumOf { it.investedAmount }
                val totalProfitLoss = groupRows.sumOf { it.profitLoss }
                val averageReturnPct = if (totalInvested > 0) (totalProfitLoss / totalInvested) * 100.0 else 0.0
                
                TrailingStopAggregateResult(
                    marketCapName = marketCapName,
                    sector = sector,
                    totalTrades = groupRows.size,
                    profitableTrades = groupRows.count { it.profitLoss > 0 },
                    totalInvested = totalInvested.roundTo2(),
                    totalProfitLoss = totalProfitLoss.roundTo2(),
                    averageReturnPct = averageReturnPct.roundTo2(),
                )
            }
            .sortedWith(compareBy<TrailingStopAggregateResult> { it.marketCapName }.thenBy { it.sector })
    }
}
