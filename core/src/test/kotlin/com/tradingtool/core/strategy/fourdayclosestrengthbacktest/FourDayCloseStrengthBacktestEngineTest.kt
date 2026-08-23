package com.tradingtool.core.strategy.fourdayclosestrengthbacktest

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FourDayCloseStrengthBacktestEngineTest {
    private val engine = FourDayCloseStrengthBacktestEngine()
    private val member = FourDayCloseStrengthMember("INFY", "Infosys", 123L)

    @Test
    fun `finds rolling four day green close strength signal and keeps twenty day detail`() {
        val candles = (0 until 24).map { index ->
            val date = LocalDate.of(2024, 1, 1).plusDays(index.toLong())
            val qualifies = index in 20..23
            candle(
                date = date,
                open = 100.0 + index,
                low = 99.0 + index,
                high = 110.0 + index,
                close = if (qualifies) 109.0 + index else 103.0 + index,
                volume = 1_000L + index,
            )
        }

        val report = engine.run(member, candles, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 24), 80.0)

        assertEquals(1, report.signalCount)
        assertEquals("2024-01-24", report.signals.single().signalDate)
        assertEquals(20, report.signals.single().lastTwentyTradingDays.size)
        assertEquals(listOf(90.91, 90.91, 90.91, 90.91), report.signals.single().patternClosePositionPct)
    }

    @Test
    fun `threshold configuration changes signal eligibility`() {
        val candles = (0 until 4).map { index ->
            candle(
                date = LocalDate.of(2024, 1, 1).plusDays(index.toLong()),
                open = 100.0,
                low = 90.0,
                high = 110.0,
                close = 105.0,
                volume = 1_000L,
            )
        }

        assertEquals(1, engine.run(member, candles, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 4), 75.0).signalCount)
        assertTrue(engine.run(member, candles, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 4), 80.0).signals.isEmpty())
    }

    private fun candle(date: LocalDate, open: Double, low: Double, high: Double, close: Double, volume: Long): DailyCandle =
        DailyCandle(123L, "INFY", date, open, high, low, close, volume)
}
