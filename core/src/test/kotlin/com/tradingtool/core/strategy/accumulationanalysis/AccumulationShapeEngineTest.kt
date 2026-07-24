package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.candle.DailyCandle
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AccumulationShapeEngineTest {
    private val engine = AccumulationShapeEngine()

    @Test
    fun `loads the packaged configuration when no local configuration is available`() {
        val config = AccumulationShapeConfigLoader.load(emptyList())

        assertEquals("v9-base-rhythm", config.algorithmVersion)
        assertEquals(60, config.shapeWindowSessions)
    }

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
        assertEquals(AccumulationShape.DOWNWARD_DRIFT, engine.classify(shapeCandles { index -> 100.0 - index * 0.4 }).shape)
        assertEquals(AccumulationShape.UPWARD_DRIFT, engine.classify(shapeCandles { index -> 100.0 + index * 0.4 }).shape)
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
        val metrics = requireNotNull(engine.classify(shapeCandles { index -> 100.0 + index * 0.4 }).metrics)

        assertEquals(true, metrics.centerSlopePerTenSessions > 0)
        assertEquals(true, metrics.startSlopePerTenSessions > 0)
        assertEquals(true, metrics.endSlopePerTenSessions > 0)
    }

    @Test
    fun `uses the newest of three consecutive twenty session chunks for Flat Golden`() {
        val candles = (0..79).map { index ->
            val close = if (index < 60) 100.0 + index else 160.0
            candle(index, close)
        }

        val analysis = engine.analyzeChain(candles, listOf(candles.last().candleDate))

        assertEquals(AccumulationShape.FLAT_GOLDEN, analysis.classification.shape)
        assertEquals(3, analysis.chunks.size)
        assertEquals(20, analysis.goldenFlatNode?.windowSessions)
        assertEquals(candles[60].candleDate, analysis.goldenFlatNode?.startDate)
    }

    @Test
    fun `BHEL reference chunk ending on March thirtieth is a Golden Flat with one ignored shock`() {
        val fixture = Path.of("..", ".claude", "requirements", "strategies", "52w-momentum", "bhel-daily-candle.json")
        val candles = Regex("\\\"candle_date\\\": \\\"([^\\\"]+)\\\"[\\s\\S]*?\\\"close\\\": \\\"([^\\\"]+)\\\"")
            .findAll(Files.readString(fixture))
            .mapIndexed { index, match -> candle(index, match.groupValues[2].toDouble()).copy(candleDate = LocalDate.parse(match.groupValues[1])) }
            .filter { it.candleDate <= LocalDate.parse("2026-03-30") }
            .sortedBy(DailyCandle::candleDate)
            .toList()
            .let { engine.windowEndingOn(it, LocalDate.parse("2026-03-30")) }

        val analysis = engine.analyzeChain(candles, listOf(LocalDate.parse("2026-03-30")))

        assertEquals(AccumulationShape.FLAT_GOLDEN, analysis.classification.shape)
        assertEquals(LocalDate.parse("2026-03-04"), analysis.classification.lineFit?.ignoredOutlierDate)
    }

    @Test
    fun `keeps a tight chunk Flat Golden after ignoring one shock`() {
        val candles = (0..79).map { index ->
            val close = when (index) {
                65 -> 110.0
                else -> 100.0
            }
            candle(index, close)
        }

        val analysis = engine.analyzeChain(candles, listOf(candles.last().candleDate))

        assertEquals(AccumulationShape.FLAT_GOLDEN, analysis.classification.shape)
        assertEquals(candles[65].candleDate, analysis.classification.lineFit?.ignoredOutlierDate)
    }

    @Test
    fun `does not use candles after the Accumulation hit`() {
        val candles = (0..99).map { index ->
            val close = if (index > 79) 150.0 else 100.0
            candle(index, close)
        }

        val analysis = engine.analyzeChain(candles, listOf(candles[79].candleDate))

        assertEquals(AccumulationShape.FLAT_GOLDEN, analysis.classification.shape)
    }

    @Test
    fun `does not call a chunk Flat after two shocks`() {
        val candles = (0..79).map { index ->
            val close = when (index) {
                65 -> 110.0
                72 -> 90.0
                else -> 100.0
            }
            candle(index, close)
        }

        val analysis = engine.analyzeChain(candles, listOf(candles.last().candleDate))

        assertNotEquals(AccumulationShape.FLAT, analysis.classification.shape)
        assertNotEquals(AccumulationShape.FLAT_GOLDEN, analysis.classification.shape)
    }

    @Test
    fun `describes the preceding sixty sessions as six forward safe ten session blocks`() {
        val candles = (0..99).map { index -> rhythmCandle(index) }

        val rhythm = requireNotNull(engine.analyzeBaseRhythm(candles, candles[79].candleDate))

        assertEquals(candles[20].candleDate, rhythm.startDate)
        assertEquals(candles[79].candleDate, rhythm.endDate)
        assertEquals(6, rhythm.blocks.size)
        assertEquals(
            listOf(
                AccumulationBaseRhythmDirection.FLAT,
                AccumulationBaseRhythmDirection.FALLING,
                AccumulationBaseRhythmDirection.RISING,
                AccumulationBaseRhythmDirection.FLAT,
                AccumulationBaseRhythmDirection.FALLING,
                AccumulationBaseRhythmDirection.RISING,
            ),
            rhythm.blocks.map(AccumulationBaseRhythmBlock::direction),
        )
    }

    private fun candle(index: Int, close: Double) = DailyCandle(1L, "BHEL", LocalDate.of(2026, 1, 1).plusDays(index.toLong()), close, close, close, close, 100L)

    private fun rhythmCandle(index: Int): DailyCandle {
        val blockPosition = index / 10
        val offset = index % 10
        val close = when (blockPosition) {
            2, 5 -> 100.0
            3, 6 -> 100.0 - (offset * 0.6)
            4, 7 -> 95.0 + (offset * 0.7)
            else -> 400.0
        }
        return DailyCandle(1L, "BHEL", LocalDate.of(2026, 1, 1).plusDays(index.toLong()), close, close * 1.01, close * 0.99, close, (blockPosition + 1L) * 100L)
    }

    private fun shapeCandles(close: (Int) -> Double): List<DailyCandle> = (0..59).map { index -> candle(index, close(index)) }

    private fun normalizedX(index: Int): Double = -1.0 + (2.0 * index / 59)
}
