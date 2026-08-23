package com.tradingtool.core.strategy.fourdayclosestrengthbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.math.round

class FourDayCloseStrengthBacktestEngine {
    internal fun run(
        member: FourDayCloseStrengthMember,
        candles: List<DailyCandle>,
        testFrom: LocalDate,
        toDate: LocalDate,
        closePositionThresholdPct: Double,
    ): FourDayCloseStrengthSymbolReport {
        require(closePositionThresholdPct in 0.0..100.0) {
            "closePositionThresholdPct must be between 0 and 100."
        }

        val sortedCandles = candles
            .filter { candle -> !candle.candleDate.isAfter(toDate) }
            .distinctBy(DailyCandle::candleDate)
            .sortedBy(DailyCandle::candleDate)
        val signals = (PATTERN_SESSIONS - 1 until sortedCandles.size)
            .mapNotNull { signalIndex ->
                val signalCandle = sortedCandles[signalIndex]
                if (signalCandle.candleDate.isBefore(testFrom)) return@mapNotNull null

                val pattern = sortedCandles.subList(signalIndex - PATTERN_SESSIONS + 1, signalIndex + 1)
                if (pattern.any { candle -> !qualifies(candle, closePositionThresholdPct) }) return@mapNotNull null

                val detailStartIndex = maxOf(0, signalIndex - DETAIL_SESSIONS + 1)
                FourDayCloseStrengthSignal(
                    symbol = member.symbol,
                    companyName = member.companyName,
                    instrumentToken = member.instrumentToken,
                    signalDate = signalCandle.candleDate.toString(),
                    patternStartDate = pattern.first().candleDate.toString(),
                    patternEndDate = pattern.last().candleDate.toString(),
                    patternClosePositionPct = pattern.map(::closeLocationPct).map(::roundTo2),
                    lastTwentyTradingDays = sortedCandles
                        .subList(detailStartIndex, signalIndex + 1)
                        .mapIndexed { index, candle -> candle.toObservation(sortedCandles, detailStartIndex + index) },
                )
            }

        return FourDayCloseStrengthSymbolReport(
            symbol = member.symbol,
            companyName = member.companyName,
            instrumentToken = member.instrumentToken,
            testedFromDate = testFrom.toString(),
            testedToDate = sortedCandles.lastOrNull()?.candleDate?.toString() ?: toDate.toString(),
            signalCount = signals.size,
            signals = signals,
        )
    }

    private fun qualifies(candle: DailyCandle, thresholdPct: Double): Boolean =
        candle.close > candle.open && closeLocationPct(candle) >= thresholdPct

    private fun closeLocationPct(candle: DailyCandle): Double {
        val range = candle.high - candle.low
        return if (range <= 0.0) 0.0 else ((candle.close - candle.low) / range) * 100.0
    }

    private fun DailyCandle.toObservation(candles: List<DailyCandle>, index: Int): FourDayCloseStrengthCandle =
        FourDayCloseStrengthCandle(
            date = candleDate.toString(),
            open = roundTo2(open),
            high = roundTo2(high),
            low = roundTo2(low),
            close = roundTo2(close),
            volume = volume,
            dailyChangePct = candles.getOrNull(index - 1)?.close
                ?.takeIf { previousClose -> previousClose > 0.0 }
                ?.let { previousClose -> roundTo2(((close - previousClose) / previousClose) * 100.0) },
            greenDay = close > open,
            openToClosePct = open.takeIf { value -> value > 0.0 }
                ?.let { value -> roundTo2(((close - value) / value) * 100.0) },
            closeLocationPct = roundTo2(closeLocationPct(this)),
        )

    private fun roundTo2(value: Double): Double = round(value * 100.0) / 100.0

    private companion object {
        const val PATTERN_SESSIONS = 4
        const val DETAIL_SESSIONS = 20
    }
}
