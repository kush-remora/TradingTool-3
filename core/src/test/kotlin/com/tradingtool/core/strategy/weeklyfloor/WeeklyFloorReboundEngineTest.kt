package com.tradingtool.core.strategy.weeklyfloor

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeeklyFloorReboundEngineTest {
    private val engine = WeeklyFloorReboundEngine()

    @Test
    fun `enters at recovery trigger and hits target`() {
        val candles = scenarioCandles()
        val entryIndex = lastSetupIndex(candles)
        val adjusted = candles
            .replace(entryIndex, candles[entryIndex].copy(open = 100.0, high = 102.0, low = 100.0, close = 101.0))
            .replace(entryIndex + 1, candles[entryIndex + 1].copy(open = 101.0, high = 107.0, low = 100.0, close = 106.0))

        val trade = report(adjusted).trades.last()

        assertEquals(WeeklyFloorReboundEngine.OUTCOME_TARGET_HIT, trade.outcome)
        assertEquals(101.0, trade.entryPrice)
        assertEquals(106.05, requireNotNull(trade.exitPrice), 0.000001)
        assertFalse(trade.gapEntry)
    }

    @Test
    fun `reports no fill when the entry day does not reach recovery trigger`() {
        val candles = scenarioCandles()
        val entryIndex = lastSetupIndex(candles)
        val adjusted = candles.replace(entryIndex, candles[entryIndex].copy(high = 100.5))

        val trade = report(adjusted).trades.last()

        assertEquals(WeeklyFloorReboundEngine.OUTCOME_NO_ENTRY, trade.outcome)
        assertNull(trade.entryPrice)
    }

    @Test
    fun `uses open price for a gap below stop`() {
        val candles = scenarioCandles()
        val entryIndex = lastSetupIndex(candles)
        val adjusted = candles.replace(entryIndex + 1, candles[entryIndex + 1].copy(open = 98.0, high = 105.0, low = 97.0, close = 99.0))

        val trade = report(adjusted).trades.last()

        assertEquals(WeeklyFloorReboundEngine.OUTCOME_STOP_LOSS, trade.outcome)
        assertEquals(98.0, trade.exitPrice)
        assertTrue(trade.gapStop)
    }

    @Test
    fun `uses stop loss for an ambiguous daily candle`() {
        val candles = scenarioCandles()
        val entryIndex = lastSetupIndex(candles)
        val adjusted = candles.replace(entryIndex + 1, candles[entryIndex + 1].copy(open = 101.0, high = 107.0, low = 99.0, close = 102.0))

        val trade = report(adjusted).trades.last()

        assertEquals(WeeklyFloorReboundEngine.OUTCOME_STOP_LOSS, trade.outcome)
        assertEquals(99.5, trade.exitPrice)
        assertTrue(trade.exitWasAmbiguous)
    }

    @Test
    fun `exits at Friday close and ignores next weeks data`() {
        val candles = scenarioCandles(days = 290)
        val entryIndex = setupIndexes(candles).dropLast(1).last()
        val fridayIndex = (entryIndex..candles.lastIndex).first { index -> candles[index].candleDate.dayOfWeek == DayOfWeek.FRIDAY }
        var adjusted = candles
        for (index in entryIndex + 1..fridayIndex) {
            adjusted = adjusted.replace(index, adjusted[index].copy(open = 101.0, high = 105.0, low = 100.0, close = 103.0))
        }
        adjusted = adjusted.replace(fridayIndex + 1, adjusted[fridayIndex + 1].copy(high = 200.0, close = 200.0))

        val trade = report(adjusted).trades.first { row -> row.setupDate == adjusted[entryIndex].candleDate.toString() }

        assertEquals(WeeklyFloorReboundEngine.OUTCOME_FRIDAY_EXIT, trade.outcome)
        assertEquals(adjusted[fridayIndex].candleDate.toString(), trade.exitDate)
        assertEquals(103.0, trade.exitPrice)
    }

    @Test
    fun `rejects a floor wider than two percent and records insufficient history`() {
        val shortReport = report(scenarioCandles(days = 250))
        assertTrue(shortReport.trades.all { row -> row.eligibilityReason == "INSUFFICIENT_HISTORY" })

        val candles = scenarioCandles()
        val entryIndex = lastSetupIndex(candles)
        val adjusted = candles.mapIndexed { index, candle ->
            if (index in entryIndex - 5 until entryIndex) candle.copy(low = 103.0) else candle
        }
        val trade = report(adjusted).trades.last()

        assertEquals(WeeklyFloorReboundEngine.OUTCOME_NOT_ELIGIBLE, trade.outcome)
        assertEquals("FLOOR_TOO_WIDE", trade.eligibilityReason)
    }

    private fun report(candles: List<DailyCandle>): WeeklyFloorReboundReport =
        engine.run("TEST", candles, candles.size)

    private fun scenarioCandles(days: Int = 280): List<DailyCandle> {
        val result = mutableListOf<DailyCandle>()
        var date = LocalDate.of(2024, 1, 1)
        while (result.size < days) {
            if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                result += DailyCandle(1L, "TEST", date, 100.0, 130.0, 100.0, 110.0, 1_000L)
            }
            date = date.plusDays(1)
        }
        return result
    }

    private fun lastSetupIndex(candles: List<DailyCandle>): Int = setupIndexes(candles).last()

    private fun setupIndexes(candles: List<DailyCandle>): List<Int> = candles.indices.filter { index ->
        index == 0 || candles[index - 1].candleDate.dayOfWeek > candles[index].candleDate.dayOfWeek
    }

    private fun List<DailyCandle>.replace(index: Int, candle: DailyCandle): List<DailyCandle> =
        mapIndexed { currentIndex, existing -> if (currentIndex == index) candle else existing }
}
