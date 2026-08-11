package com.tradingtool.core.strategy.fiftytwomomentum

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FiftyTwoWeekMomentumRule5EngineTest {
    @Test
    fun `applies the same fixed-reference rule to every supported period`() {
        val startDate = LocalDate.of(2026, 1, 1)

        listOf(20, 40, 60, 100, 200).forEach { period ->
            val candles = (0..period + 1).map { index ->
                val high = when (index) {
                    0 -> 100.0
                    period -> 101.0
                    period + 1 -> 102.0
                    else -> 99.0
                }
                candle(index, startDate, high, close = if (index == 0) 100.0 else if (index == period) 101.0 else 99.0)
            }

            val breakouts = FiftyTwoWeekMomentumRule5Engine.findRecentFreshBreakouts(
                candles = candles,
                requestedAsOfDate = startDate.plusDays((period + 1).toLong()),
                lookbackSessions = 2,
                breakoutPeriodSessions = period,
            )

            assertEquals(listOf(startDate.plusDays(period.toLong()).toString()), breakouts.map(Rule5BreakoutDay::date))
            assertEquals(100.0, breakouts[0].referenceHigh)
            assertEquals(101.0, breakouts[0].close)
            assertEquals(period, breakouts[0].referenceHighDaysAgo)
            assertEquals(1.0, breakouts[0].closeVsReferenceHighPct)
        }
    }

    @Test
    fun `accepts a close within the configured tolerance below the prior period high`() {
        val startDate = LocalDate.of(2026, 1, 1)
        val candles = (0..22).map { index ->
            when (index) {
                0 -> candle(index, startDate, high = 100.0, close = 100.0)
                20 -> candle(index, startDate, high = 99.0, close = 98.0)
                else -> candle(index, startDate, high = 95.0, close = 95.0)
            }
        }

        val breakouts = FiftyTwoWeekMomentumRule5Engine.findRecentFreshBreakouts(
            candles = candles,
            requestedAsOfDate = startDate.plusDays(22),
            lookbackSessions = 5,
            breakoutPeriodSessions = 20,
            nearHighTolerancePct = 2.0,
        )

        assertEquals(listOf(startDate.plusDays(20).toString()), breakouts.map(Rule5BreakoutDay::date))
        assertEquals(100.0, breakouts[0].referenceHigh)
        assertEquals(98.0, breakouts[0].close)
        assertEquals(20, breakouts[0].referenceHighDaysAgo)
        assertEquals(-2.0, breakouts[0].closeVsReferenceHighPct)
    }

    @Test
    fun `uses the most recent occurrence when the prior high was printed more than once`() {
        val startDate = LocalDate.of(2026, 1, 1)
        val candles = (0..22).map { index ->
            when (index) {
                0, 5 -> candle(index, startDate, high = 100.0, close = 95.0)
                20 -> candle(index, startDate, high = 99.0, close = 98.0)
                else -> candle(index, startDate, high = 95.0, close = 95.0)
            }
        }

        val breakouts = FiftyTwoWeekMomentumRule5Engine.findRecentFreshBreakouts(
            candles = candles,
            requestedAsOfDate = startDate.plusDays(22),
            lookbackSessions = 5,
            breakoutPeriodSessions = 20,
            nearHighTolerancePct = 2.0,
        )

        assertEquals(1, breakouts.size)
        assertEquals(15, breakouts[0].referenceHighDaysAgo)
    }

    @Test
    fun `rejects a breakout when an earlier high already crossed the fixed reference`() {
        val startDate = LocalDate.of(2026, 1, 1)
        val candles = (0..21).map { index ->
            val high = when {
                index == 0 -> 110.0
                index in 1..19 -> 111.0 + index
                index == 20 -> 122.0
                else -> 123.0
            }
            candle(index, startDate, high, close = high)
        }

        val breakouts = FiftyTwoWeekMomentumRule5Engine.findRecentFreshBreakouts(
            candles = candles,
            requestedAsOfDate = startDate.plusDays(21),
            lookbackSessions = 2,
            breakoutPeriodSessions = 20,
        )

        assertEquals(emptyList(), breakouts)
    }

    @Test
    fun `main scan ignores breakout events older than the latest trading-session window`() {
        val startDate = LocalDate.of(2026, 1, 1)
        val candles = (0..9).map { index ->
            val high = when (index) {
                0 -> 100.0
                1 -> 99.0
                2 -> 101.0
                else -> 99.0
            }
            candle(index, startDate, high, close = if (index == 0) 100.0 else 99.0)
        }

        val breakouts = FiftyTwoWeekMomentumRule5Engine.findRecentFreshBreakouts(
            candles = candles,
            requestedAsOfDate = startDate.plusDays(9),
            lookbackSessions = 5,
            breakoutPeriodSessions = 2,
        )

        assertEquals(emptyList(), breakouts)
    }

    private fun candle(index: Int, startDate: LocalDate, high: Double, close: Double): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "TEST",
        candleDate = startDate.plusDays(index.toLong()),
        open = close,
        high = high,
        low = close,
        close = close,
        volume = 1_000L,
    )
}
