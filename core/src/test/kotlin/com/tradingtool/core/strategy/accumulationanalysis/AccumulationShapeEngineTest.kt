package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.candle.DailyCandle
import java.time.LocalDate
import java.nio.file.Files
import java.nio.file.Path
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
    fun `classifies flat cup downward drift and invalid structures`() {
        assertEquals(AccumulationShape.FLAT, engine.classify(listOf(100.0, 102.0, 99.0, 101.0, 103.0).mapIndexed(::candle)))
        assertEquals(AccumulationShape.CUP, engine.classify(listOf(100.0, 92.0, 85.0, 90.0, 98.0).mapIndexed(::candle)))
        assertEquals(AccumulationShape.DOWNWARD_DRIFT, engine.classify(listOf(100.0, 97.0, 94.0, 90.0, 85.0).mapIndexed(::candle)))
        assertEquals(AccumulationShape.INVALID, engine.classify(listOf(100.0, 115.0, 125.0, 112.0, 90.0).mapIndexed(::candle)))
        assertEquals(AccumulationShape.UNCLASSIFIED, engine.classify(listOf(100.0, 90.0, 110.0, 95.0, 105.0, 115.0).mapIndexed(::candle)))
        assertEquals(AccumulationShapeDecision.NEEDS_REVIEW, engine.decision(AccumulationShape.UNCLASSIFIED))
    }

    @Test
    fun `BHEL reference accumulation window remains flat`() {
        val fixture = Path.of("..", ".claude", "requirements", "strategies", "52w-momentum", "bhel-daily-candle.json")
        val candles = Regex("\\\"candle_date\\\": \\\"([^\\\"]+)\\\"[\\s\\S]*?\\\"close\\\": \\\"([^\\\"]+)\\\"")
            .findAll(Files.readString(fixture))
            .mapIndexed { index, match -> candle(index, match.groupValues[2].toDouble()).copy(candleDate = LocalDate.parse(match.groupValues[1])) }
            .filter { it.candleDate in LocalDate.parse("2026-02-13")..LocalDate.parse("2026-03-27") }
            .sortedBy { it.candleDate }
            .toList()

        assertEquals(AccumulationShape.FLAT, engine.classify(candles))
    }

    private fun candle(index: Int, close: Double) = DailyCandle(1L, "BHEL", LocalDate.of(2026, 1, 1).plusDays(index.toLong()), close, close, close, close, 100L)
}
