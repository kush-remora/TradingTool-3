package com.tradingtool.core.strategy.fiftytwomomentum

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate

internal object FiftyTwoWeekMomentumRule5Engine {
    fun findRecentFreshBreakouts(
        candles: List<DailyCandle>,
        requestedAsOfDate: LocalDate,
        lookbackSessions: Int,
        breakoutPeriodSessions: Int,
        nearHighTolerancePct: Double = 0.0,
    ): List<Rule5BreakoutDay> {
        require(lookbackSessions > 0) { "lookbackSessions must be greater than zero." }
        require(breakoutPeriodSessions > 0) { "breakoutPeriodSessions must be greater than zero." }
        validateNearHighTolerance(nearHighTolerancePct)

        val orderedCandles = candles
            .filter { candle -> !candle.candleDate.isAfter(requestedAsOfDate) }
            .distinctBy(DailyCandle::candleDate)
            .sortedBy(DailyCandle::candleDate)
        val recentStartDate = orderedCandles
            .takeLast(lookbackSessions)
            .firstOrNull()
            ?.candleDate
            ?: return emptyList()
        val freshBreakouts = findFreshBreakouts(
            candles = candles,
            fromDate = recentStartDate,
            toDate = requestedAsOfDate,
            breakoutPeriodSessions = breakoutPeriodSessions,
            nearHighTolerancePct = nearHighTolerancePct,
        )

        return freshBreakouts.reversed()
    }

    fun findFreshBreakouts(
        candles: List<DailyCandle>,
        fromDate: LocalDate?,
        toDate: LocalDate,
        breakoutPeriodSessions: Int,
        nearHighTolerancePct: Double = 0.0,
    ): List<Rule5BreakoutDay> {
        require(breakoutPeriodSessions > 0) { "breakoutPeriodSessions must be greater than zero." }
        validateNearHighTolerance(nearHighTolerancePct)

        val orderedCandles = candles
            .filter { candle -> !candle.candleDate.isAfter(toDate) }
            .distinctBy(DailyCandle::candleDate)
            .sortedBy(DailyCandle::candleDate)

        return orderedCandles
            .mapNotNull { candle ->
                val currentIndex = orderedCandles.indexOfLast { candidate -> candidate.candleDate == candle.candleDate }
                if (fromDate != null && candle.candleDate.isBefore(fromDate)) {
                    null
                } else {
                    evaluateFreshBreakout(
                        orderedCandles = orderedCandles,
                        currentIndex = currentIndex,
                        breakoutPeriodSessions = breakoutPeriodSessions,
                        nearHighTolerancePct = nearHighTolerancePct,
                    )
                }
            }
    }

    private fun evaluateFreshBreakout(
        orderedCandles: List<DailyCandle>,
        currentIndex: Int,
        breakoutPeriodSessions: Int,
        nearHighTolerancePct: Double,
    ): Rule5BreakoutDay? {
        val referenceIndex = currentIndex - breakoutPeriodSessions
        if (referenceIndex < 0) return null

        val referenceHigh = (referenceIndex until currentIndex).maxOf { index -> orderedCandles[index].high }
        val referenceHighIndex = (referenceIndex until currentIndex)
            .lastOrNull { index -> orderedCandles[index].high == referenceHigh }
            ?: referenceIndex
        val acceptedLowerBound = referenceHigh * (1.0 - nearHighTolerancePct / 100.0)
        val current = orderedCandles[currentIndex]
        if (nearHighTolerancePct == 0.0) {
            if (current.close <= referenceHigh) return null
        } else if (current.close < acceptedLowerBound) {
            return null
        }

        val previousIndex = currentIndex - 1
        if (previousIndex >= 0) {
            val previousReferenceIndex = previousIndex - breakoutPeriodSessions
            if (previousReferenceIndex >= 0) {
                val previousReferenceHigh = (previousReferenceIndex until previousIndex)
                    .maxOf { index -> orderedCandles[index].high }
                val previousAcceptedLowerBound = previousReferenceHigh * (1.0 - nearHighTolerancePct / 100.0)
                if (orderedCandles[previousIndex].close >= previousAcceptedLowerBound) return null
            }
        }

        return Rule5BreakoutDay(
            date = current.candleDate.toString(),
            high = current.high,
            close = current.close,
            referenceHigh = referenceHigh,
            referenceHighDaysAgo = currentIndex - referenceHighIndex,
            closeVsReferenceHighPct = ((current.close - referenceHigh) / referenceHigh) * 100.0,
        )
    }

    private fun validateNearHighTolerance(nearHighTolerancePct: Double) {
        require(nearHighTolerancePct.isFinite() && nearHighTolerancePct in 0.0..100.0) {
            "nearHighTolerancePct must be between 0 and 100."
        }
    }
}
