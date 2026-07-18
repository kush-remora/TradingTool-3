package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.candle.DailyCandle
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AccumulationShapeEngineTest {
    private val engine = AccumulationShapeEngine()

    @Test
    fun `splits chains after fifteen trading sessions and keeps single hits`() {
        val candles = (0..40).map { index -> candle(index, 100.0) }
        val chains = engine.buildChains(listOf(candles[0].candleDate, candles[15].candleDate, candles[32].candleDate), candles)
        assertEquals(2, chains.size)
        assertEquals(2, chains.first().size)
        assertEquals(1, chains.last().size)
    }

    @Test
    fun `uses the sixty sessions ending on the final chain hit`() {
        val candles = (0..79).map { index -> candle(index, 100.0) }

        val window = engine.windowEndingOn(candles, candles[70].candleDate)

        assertEquals(60, window.size)
        assertEquals(candles[11].candleDate, window.first().candleDate)
        assertEquals(candles[70].candleDate, window.last().candleDate)
    }

    @Test
    fun `classifies regression shapes without using hit span as the candle window`() {
        assertEquals(AccumulationShape.FLAT, engine.classify(shapeCandles { 100.0 }).shape)
        assertEquals(AccumulationShape.DOWNWARD_DRIFT, engine.classify(shapeCandles { index -> 100.0 - index * 0.12 }).shape)
        assertEquals(AccumulationShape.UPWARD_DRIFT, engine.classify(shapeCandles { index -> 100.0 + index * 0.12 }).shape)
        assertEquals(AccumulationShape.CUP, engine.classify(shapeCandles { index -> 100.0 + 12.0 * normalizedX(index) * normalizedX(index) }).shape)
        assertEquals(AccumulationShape.INVALID, engine.classify(shapeCandles { index -> 100.0 - 12.0 * normalizedX(index) * normalizedX(index) }).shape)
    }

    @Test
    fun `does not call a monotonic curve a cup`() {
        val shape = engine.classify(shapeCandles { index -> 100.0 + 12.0 * (normalizedX(index) + 2.0) * (normalizedX(index) + 2.0) }).shape

        assertEquals(AccumulationShape.UPWARD_DRIFT, shape)
    }

    @Test
    fun `reports slope metrics in percentage per ten sessions`() {
        val metrics = requireNotNull(engine.classify(shapeCandles { index -> 100.0 + index * 0.12 }).metrics)

        assertEquals(true, metrics.centerSlopePerTenSessions > 0)
        assertEquals(true, metrics.startSlopePerTenSessions > 0)
        assertEquals(true, metrics.endSlopePerTenSessions > 0)
    }

    @Test
    fun `BHEL reference window ending on March thirtieth is a downward drift`() {
        val fixture = Path.of("..", ".claude", "requirements", "strategies", "52w-momentum", "bhel-daily-candle.json")
        val candles = Regex("\\\"candle_date\\\": \\\"([^\\\"]+)\\\"[\\s\\S]*?\\\"close\\\": \\\"([^\\\"]+)\\\"")
            .findAll(Files.readString(fixture))
            .mapIndexed { index, match -> candle(index, match.groupValues[2].toDouble()).copy(candleDate = LocalDate.parse(match.groupValues[1])) }
            .filter { it.candleDate <= LocalDate.parse("2026-03-30") }
            .sortedBy(DailyCandle::candleDate)
            .toList()
            .let { engine.windowEndingOn(it, LocalDate.parse("2026-03-30")) }

        assertEquals(AccumulationShape.DOWNWARD_DRIFT, engine.classify(candles).shape)
    }

    private fun candle(index: Int, close: Double) = DailyCandle(1L, "BHEL", LocalDate.of(2026, 1, 1).plusDays(index.toLong()), close, close, close, close, 100L)

    private fun shapeCandles(close: (Int) -> Double): List<DailyCandle> = (0..59).map { index -> candle(index, close(index)) }

    private fun normalizedX(index: Int): Double = -1.0 + (2.0 * index / 59)
}
