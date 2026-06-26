package com.tradingtool.core.strategy.trailingstopbacktest

import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.database.CandleJdbiHandler
import java.time.LocalDate
import com.google.inject.Inject

class TrailingStopBacktestService @Inject constructor(
    private val signalCsvSource: TrailingStopSignalCsvSource,
    private val engine: TrailingStopBacktestEngine,
    private val candleHandler: CandleJdbiHandler,
) {

    suspend fun run(config: TrailingStopBacktestConfig): TrailingStopBacktestReport {
        val signals = signalCsvSource.load(config.inputFile)
            .sortedWith(compareBy<TrailingStopSignal> { it.signalDate }.thenBy { it.symbol })
            
        if (signals.isEmpty()) {
            error("No signal rows found in ${config.inputFile}")
        }

        val earliestSignalDate = signals.minOf { it.signalDate }
        val symbols = signals.map { it.symbol }.distinct()
        
        val candlesBySymbol = loadCandlesBySymbol(
            symbols = symbols,
            fromDate = earliestSignalDate, // Load from earliest signal date
            toDate = config.priceDataToDate,
        )

        val contexts = signals.map { signal ->
            TrailingStopSymbolContext(
                signal = signal,
                candles = candlesBySymbol[signal.symbol].orEmpty(),
            )
        }

        return engine.run(
            inputFile = config.inputFile.toString(),
            priceDataToDate = config.priceDataToDate,
            allocationPerTrade = config.allocationPerTrade,
            contexts = contexts,
        )
    }

    private suspend fun loadCandlesBySymbol(
        symbols: List<String>,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): Map<String, List<com.tradingtool.core.candle.DailyCandle>> {
        return symbols.associateWith { symbol ->
            emptyList<com.tradingtool.core.candle.DailyCandle>()
        }.toMutableMap().also { candlesBySymbol ->
            symbols.forEach { symbol ->
                val candles = candleHandler.read { dao: CandleReadDao ->
                    dao.getDailyCandlesBySymbol(symbol, fromDate, toDate)
                }.sortedBy { it.candleDate }

                candlesBySymbol[symbol] = candles
            }
        }
    }
}
