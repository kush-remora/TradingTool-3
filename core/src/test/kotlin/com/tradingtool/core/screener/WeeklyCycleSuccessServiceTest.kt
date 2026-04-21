package com.tradingtool.core.screener

import com.tradingtool.core.candle.DailyCandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import java.time.LocalDate

class WeeklyCycleSuccessServiceTest {

    @Test
    fun `evaluateWeeks computes monday-friday metrics and success`() {
        val week = week(
            year = 2026,
            isoWeek = 10,
            candles = mapOf(
                1 to candle(LocalDate.of(2026, 3, 2), low = 100.0, high = 106.0, close = 104.0),
                2 to candle(LocalDate.of(2026, 3, 3), low = 101.0, high = 109.0, close = 108.0),
                5 to candle(LocalDate.of(2026, 3, 6), low = 103.0, high = 111.0, close = 105.0),
            ),
        )

        val (evaluated, failedStarts) = evaluateWeeks(
            weeks = listOf(week),
            highLowThresholdPct = 10.0,
            rocThresholdPct = 2.0,
        )

        assertTrue(failedStarts.isEmpty())
        assertEquals(1, evaluated.size)
        assertEquals(11.0, evaluated[0].highLowPct)
        assertEquals(5.0, evaluated[0].rocPct)
        assertTrue(evaluated[0].success)
        assertEquals("Mon", evaluated[0].startDay)
        assertEquals("Fri", evaluated[0].endDay)
    }

    @Test
    fun `evaluateWeeks uses tuesday when monday is missing`() {
        val week = week(
            year = 2026,
            isoWeek = 11,
            candles = mapOf(
                2 to candle(LocalDate.of(2026, 3, 10), low = 100.0, high = 108.0, close = 105.0),
                5 to candle(LocalDate.of(2026, 3, 13), low = 102.0, high = 109.0, close = 103.0),
            ),
        )

        val (evaluated, failedStarts) = evaluateWeeks(listOf(week), highLowThresholdPct = 8.0, rocThresholdPct = 3.0)

        assertTrue(failedStarts.isEmpty())
        assertEquals(1, evaluated.size)
        assertEquals("Tue", evaluated[0].startDay)
        assertEquals(9.0, evaluated[0].highLowPct)
        assertEquals(3.0, evaluated[0].rocPct)
        assertTrue(evaluated[0].success)
    }

    @Test
    fun `evaluateWeeks uses last available day when friday is missing`() {
        val week = week(
            year = 2026,
            isoWeek = 12,
            candles = mapOf(
                1 to candle(LocalDate.of(2026, 3, 16), low = 100.0, high = 104.0, close = 102.0),
                4 to candle(LocalDate.of(2026, 3, 19), low = 102.0, high = 109.0, close = 103.0),
            ),
        )

        val (evaluated, _) = evaluateWeeks(listOf(week), highLowThresholdPct = 9.0, rocThresholdPct = 3.0)

        assertEquals(1, evaluated.size)
        assertEquals("Thu", evaluated[0].endDay)
        assertTrue(evaluated[0].success)
    }

    @Test
    fun `evaluateWeeks skips week when monday and tuesday are missing`() {
        val week = week(
            year = 2026,
            isoWeek = 13,
            candles = mapOf(
                3 to candle(LocalDate.of(2026, 3, 25), low = 100.0, high = 106.0, close = 103.0),
                5 to candle(LocalDate.of(2026, 3, 27), low = 101.0, high = 108.0, close = 104.0),
            ),
        )

        val (evaluated, failedStarts) = evaluateWeeks(listOf(week), highLowThresholdPct = 5.0, rocThresholdPct = 2.0)

        assertTrue(evaluated.isEmpty())
        assertEquals(listOf("2026-W13"), failedStarts)
    }

    @Test
    fun `evaluateWeeks applies inclusive threshold and aggregates success rate inputs`() {
        val week1 = week(
            year = 2026,
            isoWeek = 14,
            candles = mapOf(
                1 to candle(LocalDate.of(2026, 3, 30), low = 100.0, high = 110.0, close = 104.0),
                5 to candle(LocalDate.of(2026, 4, 3), low = 103.0, high = 109.0, close = 102.0),
            ),
        )
        val week2 = week(
            year = 2026,
            isoWeek = 15,
            candles = mapOf(
                1 to candle(LocalDate.of(2026, 4, 6), low = 100.0, high = 108.0, close = 101.0),
                5 to candle(LocalDate.of(2026, 4, 10), low = 99.0, high = 107.0, close = 101.5),
            ),
        )

        val (evaluated, failedStarts) = evaluateWeeks(
            weeks = listOf(week1, week2),
            highLowThresholdPct = 10.0,
            rocThresholdPct = 2.0,
        )

        assertTrue(failedStarts.isEmpty())
        assertEquals(2, evaluated.size)
        assertTrue(evaluated[0].success, "First week should pass exactly on thresholds")
        assertTrue(!evaluated[1].success, "Second week should fail because high-low and roc miss threshold")
        assertEquals(10.0, evaluated[0].highLowPct)
        assertEquals(2.0, evaluated[0].rocPct)
    }

    @Test
    fun `evaluateStableBase passes when recent start lows are tight`() {
        val stable = evaluateStableBase(
            cycles = listOf(
                metrics("2026-W11", 100.0),
                metrics("2026-W12", 101.0),
                metrics("2026-W13", 102.0),
                metrics("2026-W14", 103.0),
            ),
            maxDriftPct = 4.0,
        )

        assertTrue(stable.pass)
        assertEquals(3.0, stable.driftPct)
        assertEquals(100.0, stable.lowMin)
        assertEquals(103.0, stable.lowMax)
        assertNull(stable.reason)
    }

    @Test
    fun `evaluateStableBase fails when anchor and latest lows shift too much`() {
        val unstable = evaluateStableBase(
            cycles = listOf(
                metrics("2026-W11", 100.0),
                metrics("2026-W12", 120.0),
                metrics("2026-W13", 140.0),
                metrics("2026-W14", 160.0),
            ),
            maxDriftPct = 4.0,
        )

        assertFalse(unstable.pass)
        assertEquals(60.0, unstable.driftPct)
        assertTrue(unstable.reason?.contains("Base shift") == true)
    }

    @Test
    fun `evaluateStableBase passes when shift equals threshold`() {
        val exact = evaluateStableBase(
            cycles = listOf(
                metrics("2026-W11", 100.0),
                metrics("2026-W12", 110.0),
                metrics("2026-W13", 102.0),
                metrics("2026-W14", 104.0),
            ),
            maxDriftPct = 4.0,
        )

        assertTrue(exact.pass)
        assertEquals(4.0, exact.driftPct)
    }

    @Test
    fun `evaluateStableBase fails when fewer than two valid start weeks`() {
        val insufficient = evaluateStableBase(
            cycles = listOf(
                metrics("2026-W11", 0.0),
                metrics("2026-W12", -1.0),
                metrics("2026-W13", 100.0),
            ),
            maxDriftPct = 4.0,
        )

        assertFalse(insufficient.pass)
        assertEquals("Need at least 2 valid start weeks", insufficient.reason)
        assertNull(insufficient.driftPct)
    }

    @Test
    fun `computeMondayDipPct returns null when open is invalid`() {
        val invalid = candle(date = LocalDate.of(2026, 4, 13), open = 0.0, low = 100.0, high = 105.0, close = 102.0)
        assertNull(computeMondayDipPct(invalid))
    }

    @Test
    fun `computeMondayDipMetrics uses monday then tuesday fallback and averages last 8 weeks`() {
        val weeks = (1..9).map { offset ->
            val mondayDate = LocalDate.of(2026, 1, 5).plusWeeks(offset.toLong())
            val mondayCandle = candle(
                date = mondayDate,
                open = 100.0 + offset,
                low = 98.0 + offset,
                high = 104.0 + offset,
                close = 101.0 + offset,
            )
            val tuesdayCandle = candle(
                date = mondayDate.plusDays(1),
                open = 200.0 + offset,
                low = 190.0 + offset,
                high = 205.0 + offset,
                close = 200.0 + offset,
            )
            val candles = if (offset == 9) {
                mapOf(2 to tuesdayCandle)
            } else {
                mapOf(1 to mondayCandle, 2 to tuesdayCandle)
            }
            week(year = mondayDate.year, isoWeek = mondayDate.dayOfYear, candles = candles)
        }

        val metrics = computeMondayDipMetrics(weeks, lookbackWeeks = 8)
        assertEquals(4.78, metrics.lastWeekMondayDipPct)
        assertEquals(2.26, metrics.avg8wMondayDipPct)
        assertEquals(8, metrics.mondayDipSamples8w)
    }

    @Test
    fun `computeMondayDipMetrics returns nulls when no monday or tuesday candles`() {
        val weeks = listOf(
            week(
                year = 2026,
                isoWeek = 20,
                candles = mapOf(
                    3 to candle(LocalDate.of(2026, 5, 13), open = 100.0, low = 99.0, high = 102.0, close = 101.0),
                ),
            ),
        )

        val metrics = computeMondayDipMetrics(weeks, lookbackWeeks = 8)

        assertNull(metrics.lastWeekMondayDipPct)
        assertNull(metrics.avg8wMondayDipPct)
        assertEquals(0, metrics.mondayDipSamples8w)
    }

    private fun week(year: Int, isoWeek: Int, candles: Map<Int, DailyCandle>): WeeklyCycleWeek {
        return WeeklyCycleWeek(
            isoYear = year,
            isoWeek = isoWeek,
            dailyCandles = candles,
        )
    }

    private fun candle(
        date: LocalDate,
        low: Double,
        high: Double,
        close: Double,
        open: Double = close,
    ): DailyCandle {
        return DailyCandle(
            instrumentToken = 1,
            symbol = "TEST",
            candleDate = date,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = 1000,
        )
    }

    private fun metrics(weekLabel: String, startLow: Double): WeeklyCycleMetrics {
        return WeeklyCycleMetrics(
            weekLabel = weekLabel,
            startDay = "Mon",
            endDay = "Fri",
            startLow = startLow,
            endClose = startLow,
            weekHigh = startLow,
            highLowPct = 0.0,
            rocPct = 0.0,
            success = false,
        )
    }
}
