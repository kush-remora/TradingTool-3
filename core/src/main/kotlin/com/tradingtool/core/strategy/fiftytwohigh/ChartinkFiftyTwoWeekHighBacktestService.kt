package com.tradingtool.core.strategy.fiftytwohigh

import com.tradingtool.core.candle.dao.CandleReadDao
import com.tradingtool.core.database.CandleJdbiHandler
import java.time.LocalDate

class ChartinkFiftyTwoWeekHighBacktestService(
    private val signalCsvSource: ChartinkFiftyTwoWeekHighSignalCsvSource,
    private val engine: ChartinkFiftyTwoWeekHighBacktestEngine,
    private val candleHandler: CandleJdbiHandler,
) {

    suspend fun run(config: ChartinkFiftyTwoWeekHighBacktestConfig): ChartinkFiftyTwoWeekHighBacktestReport {
        val signals = signalCsvSource.load(config.inputFile)
            .sortedWith(compareBy<ChartinkFiftyTwoWeekHighSignal> { it.signalDate }.thenBy { it.symbol })
        if (signals.isEmpty()) {
            error("No signal rows found in ${config.inputFile}")
        }

        val earliestSignalDate = signals.minOf { signal -> signal.signalDate }
        val candlesBySymbol = loadCandlesBySymbol(
            symbols = signals.map { signal -> signal.symbol }.distinct(),
            fromDate = earliestSignalDate,
            toDate = config.priceDataToDate,
        )

        val contexts = signals.map { signal ->
            ChartinkFiftyTwoWeekHighSymbolContext(
                signal = signal,
                candles = candlesBySymbol[signal.symbol].orEmpty(),
            )
        }

        return engine.run(
            inputFile = config.inputFile.toString(),
            priceDataToDate = config.priceDataToDate,
            strategies = config.strategies,
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
                }.sortedBy { candle -> candle.candleDate }

                candlesBySymbol[symbol] = candles
            }
        }
    }
}
