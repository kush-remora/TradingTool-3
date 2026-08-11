package com.tradingtool.core.strategy.fiftytwomomentum

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FiftyTwoWeekMomentumRule5BacktestEngineTest {
    @Test
    fun `enters at breakout close and exits at the first later target touch`() {
        val startDate = LocalDate.of(2026, 1, 1)
        val candles = (0..22).map { index ->
            val high = when (index) {
                0 -> 100.0
                in 1..19 -> 99.0
                20 -> 105.0
                22 -> 115.0
                else -> 109.0
            }
            candle(index, startDate, high, close = if (index == 20) 104.0 else high)
        }

        val evaluation = FiftyTwoWeekMomentumRule5BacktestEngine.evaluate(
            symbol = "TEST",
            companyName = "Test Company",
            candles = candles,
            periodStartDate = startDate,
            requestedAsOfDate = startDate.plusDays(22),
            breakoutPeriodSessions = 20,
            targetPct = 10.0,
        )

        assertEquals(1, evaluation.trades.size)
        assertEquals(startDate.plusDays(20).toString(), evaluation.trades[0].entryDate)
        assertEquals(104.0, evaluation.trades[0].entryPrice)
        assertEquals(114.4, evaluation.trades[0].targetPrice)
        assertEquals(startDate.plusDays(22).toString(), evaluation.trades[0].exitDate)
        assertEquals(115.0, evaluation.trades[0].latestPrice)
        assertEquals(((115.0 - 104.0) / 104.0) * 100.0, evaluation.trades[0].changeFromEntryPct)
        assertEquals(2, evaluation.trades[0].holdingTradingDays)
        assertEquals("TARGET_HIT", evaluation.trades[0].status)
    }

    @Test
    fun `keeps a trade open and skips later signals for the same stock`() {
        val startDate = LocalDate.of(2026, 1, 1)
        val candles = listOf(
            candle(0, startDate, high = 100.0, close = 100.0),
            candle(1, startDate, high = 99.0, close = 99.0),
            candle(2, startDate, high = 101.0, close = 100.0),
            candle(3, startDate, high = 105.0, close = 105.0),
            candle(4, startDate, high = 104.0, close = 95.0),
            candle(5, startDate, high = 106.0, close = 105.0),
        )

        val evaluation = FiftyTwoWeekMomentumRule5BacktestEngine.evaluate(
            symbol = "TEST",
            companyName = "Test Company",
            candles = candles,
            periodStartDate = startDate,
            requestedAsOfDate = startDate.plusDays(5),
            breakoutPeriodSessions = 2,
            nearHighTolerancePct = 2.0,
            targetPct = 10.0,
        )

        assertEquals(2, evaluation.signals.size)
        assertEquals(listOf("ENTERED", "SKIPPED_OPEN_POSITION"), evaluation.signals.map(Rule5BacktestSignal::outcome))
        assertEquals(1, evaluation.trades.size)
        assertEquals("OPEN", evaluation.trades[0].status)
        assertEquals(105.0, evaluation.trades[0].latestPrice)
        assertEquals(5.0, evaluation.trades[0].changeFromEntryPct)
        assertEquals(3, evaluation.trades[0].holdingTradingDays)
    }

    private fun candle(index: Int, startDate: LocalDate, high: Double, close: Double): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "TEST",
        candleDate = startDate.plusDays(index.toLong()),
        open = close,
        high = high,
        low = minOf(high, close),
        close = close,
        volume = 1_000L,
    )
}
