package com.tradingtool.core.strategy.hotsma

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.abs
import java.time.LocalDate

class HotSmaScannerServiceTest {

    @Test
    fun `low touch on SMA100 in last 5 sessions resolves to standard buy`() {
        val candles = buildRisingCandles(220)
        val sma100 = calculateRollingSmaValues(candles, window = HotSmaScannerService.SMA100_WINDOW)
        val sma200 = calculateRollingSmaValues(candles, window = HotSmaScannerService.SMA200_WINDOW)

        val last = candles.lastIndex
        val adjustedLow = (sma100[last] - 0.5).coerceAtLeast(sma200[last] + 0.1)
        val updated = candles.toMutableList()
        updated[last] = updated[last].copy(low = adjustedLow)

        val touch100 = detectLatestLowTouch(updated, sma100, lookbackDays = HotSmaScannerService.TOUCH_LOOKBACK_DAYS)
        val touch200 = detectLatestLowTouch(updated, sma200, lookbackDays = HotSmaScannerService.TOUCH_LOOKBACK_DAYS)

        assertNotNull(touch100)
        assertNull(touch200)
        assertEquals(
            HotSmaSignalTag.STANDARD_BUY,
            resolveHotSmaSignalTag(
                sma100TouchDate = touch100,
                sma200TouchDate = touch200,
                pctToSma200 = null,
            ),
        )
    }

    @Test
    fun `low touch on SMA200 in last 5 sessions resolves to aggressive buy`() {
        val candles = buildRisingCandles(220)
        val sma100 = calculateRollingSmaValues(candles, window = HotSmaScannerService.SMA100_WINDOW)
        val sma200 = calculateRollingSmaValues(candles, window = HotSmaScannerService.SMA200_WINDOW)

        val last = candles.lastIndex
        val updated = candles.toMutableList()
        updated[last] = updated[last].copy(low = sma200[last] - 0.5)

        val touch100 = detectLatestLowTouch(updated, sma100, lookbackDays = HotSmaScannerService.TOUCH_LOOKBACK_DAYS)
        val touch200 = detectLatestLowTouch(updated, sma200, lookbackDays = HotSmaScannerService.TOUCH_LOOKBACK_DAYS)

        assertNotNull(touch100)
        assertNotNull(touch200)
        assertEquals(
            HotSmaSignalTag.AGGRESSIVE_BUY,
            resolveHotSmaSignalTag(
                sma100TouchDate = touch100,
                sma200TouchDate = touch200,
                pctToSma200 = null,
            ),
        )
    }

    @Test
    fun `aggressive takes precedence when both touch conditions are true`() {
        val signal = resolveHotSmaSignalTag(
            sma100TouchDate = LocalDate.of(2026, 5, 30),
            sma200TouchDate = LocalDate.of(2026, 5, 31),
            pctToSma200 = 4.0,
        )

        assertEquals(HotSmaSignalTag.AGGRESSIVE_BUY, signal)
    }

    @Test
    fun `watch zone is true only for pct in zero to ten`() {
        assertEquals(
            HotSmaSignalTag.WATCH_ZONE,
            resolveHotSmaSignalTag(sma100TouchDate = null, sma200TouchDate = null, pctToSma200 = 0.0),
        )
        assertEquals(
            HotSmaSignalTag.WATCH_ZONE,
            resolveHotSmaSignalTag(sma100TouchDate = null, sma200TouchDate = null, pctToSma200 = 10.0),
        )
        assertNull(resolveHotSmaSignalTag(sma100TouchDate = null, sma200TouchDate = null, pctToSma200 = -0.01))
        assertNull(resolveHotSmaSignalTag(sma100TouchDate = null, sma200TouchDate = null, pctToSma200 = 10.01))
    }

    @Test
    fun `sma200 uses available history when only 120 candles exist`() {
        val candles = buildRisingCandles(120)
        val sma200 = calculateRollingSmaValues(candles, window = HotSmaScannerService.SMA200_WINDOW)

        val expected = candles.map { candle -> candle.close }.average()
        assertEquals(120, sma200.size)
        assertEquals(expected, sma200.last(), 1e-9)
    }

    @Test
    fun `empty candle list returns no touches and no signals`() {
        assertEquals(emptyList<Double>(), calculateRollingSmaValues(emptyList(), 100))
        assertNull(detectLatestLowTouch(emptyList(), emptyList(), lookbackDays = 5))
        assertNull(resolveHotSmaSignalTag(sma100TouchDate = null, sma200TouchDate = null, pctToSma200 = null))
    }

    @Test
    fun `zone status marks anything within five percent of sma200 as buy zone`() {
        assertEquals(HotSmaZoneStatus.BUY_ZONE, resolveHotSmaZoneStatus(-2.5))
        assertEquals(HotSmaZoneStatus.BUY_ZONE, resolveHotSmaZoneStatus(0.0))
        assertEquals(HotSmaZoneStatus.BUY_ZONE, resolveHotSmaZoneStatus(5.0))
        assertEquals(HotSmaZoneStatus.ABOVE_BUY_ZONE, resolveHotSmaZoneStatus(5.01))
        assertEquals(HotSmaZoneStatus.NO_SMA200, resolveHotSmaZoneStatus(null))
    }

    @Test
    fun `consecutive red days counts close below previous close from latest backward`() {
        val candles = listOf(
            candle(close = 100.0, high = 101.0),
            candle(dateOffset = 1, close = 99.0, high = 100.0),
            candle(dateOffset = 2, close = 98.0, high = 99.0),
            candle(dateOffset = 3, close = 97.0, high = 98.0),
            candle(dateOffset = 4, close = 98.0, high = 99.0),
        )

        assertEquals(0, countConsecutiveRedDays(candles))
        assertEquals(3, countConsecutiveRedDays(candles.dropLast(1)))
    }

    @Test
    fun `move3d percent compares latest close with close three sessions ago`() {
        val candles = listOf(
            candle(close = 100.0, high = 101.0),
            candle(dateOffset = 1, close = 102.0, high = 103.0),
            candle(dateOffset = 2, close = 104.0, high = 105.0),
            candle(dateOffset = 3, close = 106.0, high = 107.0),
        )

        val move3dPct = requireNotNull(computeMove3dPct(candles))
        assertEquals(6.0, move3dPct, 1e-9)
        assertNull(computeMove3dPct(candles.take(3)))
    }

    @Test
    fun `drawdown from recent high uses highest high in requested window`() {
        val candles = listOf(
            candle(close = 100.0, high = 105.0),
            candle(dateOffset = 1, close = 104.0, high = 110.0),
            candle(dateOffset = 2, close = 102.0, high = 107.0),
        )

        val drawdown = computeDrawdownFromRecentHighPct(candles, lookbackDays = 3)
        val resolvedDrawdown = requireNotNull(drawdown)
        assertEquals(abs((102.0 / 110.0 - 1.0) * 100.0), abs(resolvedDrawdown), 1e-9)
    }

    private fun buildRisingCandles(size: Int): List<DailyCandle> {
        return (0 until size).map { offset ->
            val close = 100.0 + offset
            DailyCandle(
                instrumentToken = 101L,
                symbol = "TEST",
                candleDate = LocalDate.of(2025, 1, 1).plusDays(offset.toLong()),
                open = close - 1.0,
                high = close + 1.0,
                low = close - 1.0,
                close = close,
                volume = 1_000L,
            )
        }
    }

    private fun candle(
        close: Double,
        high: Double,
        dateOffset: Int = 0,
    ): DailyCandle {
        return DailyCandle(
            instrumentToken = 101L,
            symbol = "TEST",
            candleDate = LocalDate.of(2025, 1, 1).plusDays(dateOffset.toLong()),
            open = close,
            high = high,
            low = close - 1.0,
            close = close,
            volume = 1_000L,
        )
    }
}
