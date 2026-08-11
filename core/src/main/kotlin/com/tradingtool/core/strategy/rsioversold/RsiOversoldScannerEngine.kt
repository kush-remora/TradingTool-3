package com.tradingtool.core.strategy.rsioversold

import com.tradingtool.core.candle.DailyCandle
import com.tradingtool.core.technical.roundTo2
import java.time.LocalDate

class RsiOversoldScannerEngine {
    fun evaluate(
        symbol: String,
        companyName: String?,
        watchlistKeys: List<String>,
        candles: List<DailyCandle>,
        rsiValues: List<Double>,
        asOfDate: LocalDate,
    ): RsiOversoldRow? {
        require(candles.size == rsiValues.size) { "Candle and RSI series must have the same size." }

        val available = candles
            .sortedBy(DailyCandle::candleDate)
            .zip(rsiValues)
            .filter { (candle, _) -> !candle.candleDate.isAfter(asOfDate) }
        if (available.size < BASELINE_SESSIONS + SIGNAL_WINDOW_SESSIONS) return null

        val latest = available.last().first
        val signalWindowStart = available.size - SIGNAL_WINDOW_SESSIONS
        val baselineStart = signalWindowStart - BASELINE_SESSIONS
        val baseline = available.subList(baselineStart, signalWindowStart)
        val baselineRsiLow = baseline.minOf { pair -> pair.second.roundTo2() }
        val signal = available
            .subList(signalWindowStart, available.size)
            .lastOrNull { (_, rsi) -> (rsi + SIGNAL_OFFSET).roundTo2() == baselineRsiLow }
            ?: return null
        val signalCandle = signal.first

        return RsiOversoldRow(
            symbol = symbol.trim().uppercase(),
            companyName = companyName,
            watchlistKeys = watchlistKeys.sorted(),
            signalDate = signalCandle.candleDate.toString(),
            signalRsi = signal.second.roundTo2(),
            signalPrice = signalCandle.close.roundTo2(),
            signalVolume = signalCandle.volume,
            baselineRsiLow = baselineRsiLow,
            latestDate = latest.candleDate.toString(),
            latestClose = latest.close.roundTo2(),
            latestVolume = latest.volume,
        )
    }

    private companion object {
        const val BASELINE_SESSIONS: Int = 200
        const val SIGNAL_WINDOW_SESSIONS: Int = 20
        const val SIGNAL_OFFSET: Double = 1.0
    }
}
