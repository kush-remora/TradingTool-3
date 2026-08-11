package com.tradingtool.core.strategy.volumeeventbacktest

import com.tradingtool.core.candle.DailyCandle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class VolumeEventConfirmationBacktestEngineTest {
    private val engine = VolumeEventConfirmationBacktestEngine()
    private val member = VolumeEventConfirmationMember("TEST", "Test Company", 1L)
    private val fromDate = LocalDate.of(2025, 1, 6)
    private val toDate = LocalDate.of(2025, 2, 10)

    @Test
    fun `enters when today's close is below the previous volume shocker close`() {
        val candles = volumeShockerScenario(signalClose = 95.0, entryClose = 114.0, entryHigh = 114.0)

        val report = engine.run(
            member = member,
            candles = candles,
            fromDate = fromDate,
            toDate = toDate,
        )

        val observation = report.observations.single()
        assertEquals(1, report.summary.confirmedSignalCount)
        assertEquals(1, report.summary.targetHitCount)
        assertEquals(VolumeEventConfirmationStatuses.TARGET_HIT, observation.status)
        assertEquals("2025-01-11", observation.eventDate)
        assertEquals("2025-01-16", observation.entrySignalDate)
        assertEquals("2025-01-17", observation.entryDate)
        assertEquals(100.0, observation.entryPrice)
        assertEquals(110.0, observation.targetPrice)
    }

    @Test
    fun `keeps an open trade with current LTP and percentage change`() {
        val candles = volumeShockerScenario(signalClose = 95.0, entryClose = 104.0, entryHigh = 106.0)

        val report = engine.run(
            member = member,
            candles = candles,
            fromDate = fromDate,
            toDate = toDate,
        )

        val observation = report.observations.single()
        assertEquals(VolumeEventConfirmationStatuses.UNRESOLVED, observation.status)
        assertEquals(104.0, observation.currentLtp)
        assertEquals(4.0, observation.currentLtpChangePct)
        assertEquals(null, observation.exitDate)
        assertEquals(null, observation.exitPrice)
    }

    @Test
    fun `does not enter when today's close is not below the previous volume shocker close`() {
        val candles = volumeShockerScenario(signalClose = 96.0, entryClose = 104.0, entryHigh = 106.0)

        val report = engine.run(
            member = member,
            candles = candles,
            fromDate = fromDate,
            toDate = toDate,
        )

        assertEquals(0, report.summary.confirmedSignalCount)
        assertEquals(0, report.observations.size)
    }

    @Test
    fun `does not enter when today's volume is not a new volume shocker`() {
        val candles = volumeShockerScenario(
            signalClose = 95.0,
            entryClose = 104.0,
            entryHigh = 106.0,
            signalVolume = 100,
        )

        val report = engine.run(
            member = member,
            candles = candles,
            fromDate = fromDate,
            toDate = toDate,
        )

        assertEquals(0, report.summary.confirmedSignalCount)
        assertEquals(0, report.observations.size)
    }

    @Test
    fun `keeps checking the full backtest window for a target hit`() {
        val candles = volumeShockerScenario(signalClose = 95.0, entryClose = 104.0, entryHigh = 106.0) +
            (17..31).map { index -> candle(index, 104.0, 104.0, high = 106.0) } +
            listOf(candle(32, 104.0, 112.0, high = 112.0))

        val report = engine.run(
            member = member,
            candles = candles,
            fromDate = fromDate,
            toDate = toDate,
        )

        val observation = report.observations.single()
        assertEquals(VolumeEventConfirmationStatuses.TARGET_HIT, observation.status)
        assertEquals("2025-02-02", observation.exitDate)
        assertEquals(17, observation.holdingTradingDays)
    }

    private fun volumeShockerScenario(
        signalClose: Double,
        entryClose: Double,
        entryHigh: Double,
        signalVolume: Long = 300,
    ): List<DailyCandle> = listOf(
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
        candle(10, 95.0, 96.0, volume = 300),
        candle(11, 96.0, 97.0),
        candle(12, 97.0, 98.0),
        candle(13, 98.0, 97.0),
        candle(14, 97.0, 96.0),
        candle(15, 96.0, signalClose, volume = signalVolume),
        candle(16, 100.0, entryClose, high = entryHigh),
    )

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
