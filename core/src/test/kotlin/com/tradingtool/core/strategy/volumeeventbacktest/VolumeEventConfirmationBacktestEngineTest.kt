package com.tradingtool.core.strategy.volumeeventbacktest

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VolumeEventConfirmationBacktestEngineTest {
    private val engine = VolumeEventConfirmationBacktestEngine(
        adaptiveRsiCalibrationCalculator = object : AdaptiveRsiCalibrationProvider {
            override fun calculate(
                candles: List<DailyCandle>,
                rsiValues: List<Double>,
                currentEventIndex: Int,
                config: VolumeEventConfirmationBacktestConfig,
            ): AdaptiveRsiCalibration = AdaptiveRsiCalibration(
                threshold = 100.0,
                sampleCount = 10,
                baselineHitRatePct = 40.0,
                selectedHitRatePct = 60.0,
            )
        },
    )
    private val member = VolumeEventConfirmationMember("TEST", "Test Company", 1L)
    private val fromDate = LocalDate.of(2025, 1, 6)
    private val toDate = LocalDate.of(2025, 1, 20)

    @Test
    fun `enters after five session RSI improvement and records the five percent target`() {
        val candles = listOf(
            candle(0, 100.0, 100.0),
            candle(1, 100.0, 99.0),
            candle(2, 99.0, 98.0),
            candle(3, 98.0, 97.0),
            candle(4, 97.0, 96.0),
            candle(5, 96.0, 95.0),
            candle(6, 95.0, 94.0),
            candle(7, 94.0, 93.0),
            candle(8, 93.0, 92.0),
            candle(9, 92.0, 91.0),
            candle(10, 95.0, 96.0, volume = 300, high = 97.0),
            candle(11, 90.0, 91.0),
            candle(12, 91.0, 92.0),
            candle(13, 92.0, 93.0),
            candle(14, 93.0, 94.0),
            candle(15, 94.0, 95.0),
            candle(16, 100.0, 104.0, high = 106.0),
        )

        val report = engine.run(
            member = member,
            candles = candles,
            fromDate = fromDate,
            toDate = toDate,
            config = VolumeEventConfirmationBacktestConfig(),
        )

        val observation = report.observations.single()
        assertEquals(1, report.summary.setupCount)
        assertEquals(1, report.summary.confirmedSignalCount)
        assertEquals(1, report.summary.targetHitCount)
        assertEquals("2025-01-17", observation.entryDate)
        assertEquals(100.0, observation.entryPrice)
        assertEquals(105.0, observation.targetPrice)
        assertEquals(VolumeEventConfirmationStatuses.TARGET_HIT, observation.status)
        assertEquals(1, observation.holdingTradingDays)
    }

    @Test
    fun `enters early when RSI improves from five sessions before through the day before the event`() {
        val candles = listOf(
            candle(0, 95.0, 95.0),
            candle(1, 95.0, 94.0),
            candle(2, 94.0, 93.0),
            candle(3, 93.0, 94.0),
            candle(4, 94.0, 93.0),
            candle(5, 93.0, 92.0),
            candle(6, 92.0, 91.0),
            candle(7, 91.0, 92.0),
            candle(8, 92.0, 91.0),
            candle(9, 91.0, 94.0),
            candle(10, 94.0, 95.0, volume = 300, high = 97.0),
            candle(11, 90.0, 91.0, high = 100.0),
            candle(12, 91.0, 92.0),
            candle(13, 92.0, 93.0),
            candle(14, 93.0, 94.0),
            candle(15, 94.0, 95.0),
            candle(16, 100.0, 104.0, high = 106.0),
        )

        val report = engine.run(
            member = member,
            candles = candles,
            fromDate = fromDate,
            toDate = toDate,
            config = VolumeEventConfirmationBacktestConfig(
                entryMode = VolumeEventEntryModes.FIVE_DAY_PAST_RSI_EARLY_ENTRY,
                rsiPeriod = 3,
            ),
        )

        val observation = report.observations.single()
        assertEquals(1, report.summary.confirmedSignalCount)
        assertEquals(VolumeEventConfirmationStatuses.TARGET_HIT, observation.status)
        assertEquals("2025-01-12", observation.entryDate)
        assertEquals(null, observation.confirmationDate)
        assertEquals(true, observation.pastRsiChangePoints!! > 0.0)
        assertEquals(true, observation.pastRsiTrendPassed)
    }

    @Test
    fun `keeps a low RSI volume event when RSI does not improve`() {
        val candles = (0..16).map { index ->
            when {
                index == 10 -> candle(index, 91.0, 90.0, volume = 300)
                index < 10 -> candle(index, 80.0 + index, 79.0 + index)
                else -> candle(index, 100.0 - index, 99.0 - index)
            }
        }

        val report = engine.run(
            member = member,
            candles = candles,
            fromDate = fromDate,
            toDate = toDate,
            config = VolumeEventConfirmationBacktestConfig(),
        )

        assertEquals(1, report.summary.setupCount)
        assertEquals(0, report.summary.confirmedSignalCount)
        assertEquals(1, report.summary.noConfirmationCount)
        assertEquals(VolumeEventConfirmationStatuses.NO_CONFIRMATION, report.observations.single().status)
    }

    @Test
    fun `rejects a volume event when the five-session price context is bearish`() {
        val candles = (0..16).map { index ->
            when (index) {
                10 -> candle(index, 91.0, 90.0, volume = 300)
                else -> candle(index, 100.0 - index, 99.0 - index)
            }
        }

        val report = engine.run(
            member = member,
            candles = candles,
            fromDate = fromDate,
            toDate = toDate,
            config = VolumeEventConfirmationBacktestConfig(),
        )

        assertEquals(1, report.summary.setupCount)
        assertEquals(1, report.summary.rejectedBearishContextCount)
        assertEquals(VolumeEventConfirmationStatuses.REJECTED_BEARISH_CONTEXT, report.observations.single().status)
    }

    private fun candle(
        index: Int,
        open: Double,
        close: Double,
        volume: Long = 100,
        high: Double = maxOf(open, close),
    ): DailyCandle = DailyCandle(
        instrumentToken = 1L,
        symbol = "TEST",
        candleDate = LocalDate.of(2025, 1, 1).plusDays(index.toLong()),
        open = open,
        high = high,
        low = minOf(open, close),
        close = close,
        volume = volume,
    )
}
