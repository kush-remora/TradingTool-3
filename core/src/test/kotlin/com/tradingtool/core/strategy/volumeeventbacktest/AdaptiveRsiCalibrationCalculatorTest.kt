package com.tradingtool.core.strategy.volumeeventbacktest

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AdaptiveRsiCalibrationCalculatorTest {
    private val calculator = AdaptiveRsiCalibrationCalculator()

    @Test
    fun `selects the highest RSI ceiling with better hit rate than the stock baseline`() {
        val candles = (0..75).map { index ->
            val isEvent = index == 10 || index == 31 || index == 52 || index == 73
            val high = when (index) {
                16, 37 -> 106.0
                else -> 100.0
            }
            candle(index, volume = if (isEvent) 300 else 100, high = high)
        }
        val rsiValues = List(76) { 50.0 }.toMutableList().apply {
            this[10] = 30.0
            this[31] = 30.0
            this[52] = 60.0
            this[73] = 30.0
            this[15] = 35.0
            this[36] = 35.0
            this[57] = 55.0
        }

        val calibration = calculator.calculate(
            candles = candles,
            rsiValues = rsiValues,
            currentEventIndex = 73,
            config = VolumeEventConfirmationBacktestConfig(
                targetPct = 5.0,
                adaptiveRsiMinimumSampleCount = 2,
            ),
        )

        assertEquals(3, calibration.sampleCount)
        assertEquals(30.0, calibration.threshold)
        assertEquals(66.67, calibration.baselineHitRatePct)
        assertEquals(100.0, calibration.selectedHitRatePct)
    }

    private fun candle(index: Int, volume: Long, high: Double): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "TEST",
        candleDate = LocalDate.of(2025, 1, 1).plusDays(index.toLong()),
        open = 100.0,
        high = high,
        low = 99.0,
        close = 100.0,
        volume = volume,
    )
}
