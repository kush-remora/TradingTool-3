package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.strategy.accumulationanalysis.dao.confirmationDatesFrom
import com.tradingtool.core.strategy.accumulationanalysis.dao.shapeMetricsFrom
import com.tradingtool.core.strategy.accumulationanalysis.dao.lineFitFrom
import com.tradingtool.core.strategy.accumulationanalysis.dao.goldenFlatNodeFrom
import com.tradingtool.core.strategy.accumulationanalysis.dao.shapeChunksFrom
import com.tradingtool.core.strategy.accumulationanalysis.dao.baseRhythmFrom
import kotlin.test.Test
import kotlin.test.assertEquals

class AccumulationAnalysisDaoTest {
    @Test
    fun `ignores blank legacy confirmation dates`() {
        val dates = confirmationDatesFrom("""{"phaseDDates":["", "2026-06-25"],"freshBreakoutDates":[],"fiftyTwoWeekHighDates":[""]}""")

        assertEquals(listOf("2026-06-25"), dates.phaseD.map { it.toString() })
        assertEquals(emptyList(), dates.freshBreakout)
        assertEquals(emptyList(), dates.fiftyTwoWeekHigh)
    }

    @Test
    fun `reads persisted regression decision metrics`() {
        val metrics = requireNotNull(shapeMetricsFrom("""{"regression":{"curvature":0.08,"centerSlopePerTenSessions":0.4,"startSlopePerTenSessions":-0.5,"endSlopePerTenSessions":1.3,"vertexPosition":-0.25}}"""))

        assertEquals(0.08, metrics.curvature)
        assertEquals(-0.5, metrics.startSlopePerTenSessions)
        assertEquals(-0.25, metrics.vertexPosition)
    }

    @Test
    fun `reads the persisted Golden Flat node`() {
        val node = requireNotNull(goldenFlatNodeFrom("""{"goldenFlatNode":{"windowSessions":20,"startDate":"2026-05-13","endDate":"2026-06-09","metrics":{"curvature":0.001,"centerSlopePerTenSessions":0.1,"startSlopePerTenSessions":0.08,"endSlopePerTenSessions":0.12,"vertexPosition":null},"lineFit":{"slopePerTenSessions":0.1,"typicalDeviationPercent":0.4,"maximumDeviationPercent":0.8,"ignoredOutlierDate":"2026-05-20","ignoredOutlierDeviationPercent":4.1}}}"""))

        assertEquals(20, node.windowSessions)
        assertEquals("2026-06-09", node.endDate.toString())
        assertEquals("2026-05-20", node.lineFit?.ignoredOutlierDate.toString())
    }

    @Test
    fun `reads persisted straight line diagnostics`() {
        val lineFit = requireNotNull(lineFitFrom("""{"lineFit":{"slopePerTenSessions":-1.2,"typicalDeviationPercent":0.7,"maximumDeviationPercent":1.6,"ignoredOutlierDate":"2026-03-04","ignoredOutlierDeviationPercent":4.4}}"""))

        assertEquals(-1.2, lineFit.slopePerTenSessions)
        assertEquals("2026-03-04", lineFit.ignoredOutlierDate.toString())
    }

    @Test
    fun `reads legacy JSON date arrays in chunk diagnostics`() {
        val chunks = shapeChunksFrom("""{"latestShapeChunks":[{"position":1,"startDate":[2026,4,14],"endDate":[2026,5,11],"shape":"FLAT","goldenFlat":false,"metrics":{"curvature":0.001,"centerSlopePerTenSessions":0.1,"startSlopePerTenSessions":0.08,"endSlopePerTenSessions":0.12,"vertexPosition":null},"lineFit":{"slopePerTenSessions":0.1,"typicalDeviationPercent":0.4,"maximumDeviationPercent":0.8,"ignoredOutlierDate":[2026,4,20],"ignoredOutlierDeviationPercent":4.1}}]}""")

        assertEquals("2026-04-14", chunks.single().startDate.toString())
        assertEquals("2026-04-20", chunks.single().lineFit?.ignoredOutlierDate.toString())
    }

    @Test
    fun `reads the latest three chunk path`() {
        val chunks = shapeChunksFrom("""{"latestShapeChunks":[{"position":1,"startDate":"2026-04-14","endDate":"2026-05-11","shape":"FLAT","goldenFlat":false,"metrics":{"curvature":0.001,"centerSlopePerTenSessions":0.1,"startSlopePerTenSessions":0.08,"endSlopePerTenSessions":0.12,"vertexPosition":null}},{"position":2,"startDate":"2026-05-12","endDate":"2026-06-09","shape":"FLAT","goldenFlat":false,"metrics":{"curvature":0.001,"centerSlopePerTenSessions":0.1,"startSlopePerTenSessions":0.08,"endSlopePerTenSessions":0.12,"vertexPosition":null}},{"position":3,"startDate":"2026-06-10","endDate":"2026-07-07","shape":"FLAT_GOLDEN","goldenFlat":true,"metrics":{"curvature":0.001,"centerSlopePerTenSessions":0.1,"startSlopePerTenSessions":0.08,"endSlopePerTenSessions":0.12,"vertexPosition":null},"lineFit":{"slopePerTenSessions":0.1,"typicalDeviationPercent":0.4,"maximumDeviationPercent":0.8,"ignoredOutlierDate":"2026-06-25","ignoredOutlierDeviationPercent":4.1}}]}""")

        assertEquals(listOf(AccumulationShape.FLAT, AccumulationShape.FLAT, AccumulationShape.FLAT_GOLDEN), chunks.map(AccumulationShapeChunk::shape))
        assertEquals("2026-06-25", chunks.last().lineFit?.ignoredOutlierDate.toString())
    }

    @Test
    fun `reads persisted base rhythm blocks`() {
        val rhythm = requireNotNull(baseRhythmFrom("""{"baseRhythm":{"startDate":"2026-04-22","endDate":"2026-07-17","blocks":[{"position":1,"startDate":"2026-04-22","endDate":"2026-05-05","direction":"FLAT","rangeState":"STEADY","volumeState":"STEADY","closeChangePercent":0.5,"rangePercent":4.2,"averageVolume":1200000.0}]}}"""))

        assertEquals("2026-04-22", rhythm.startDate.toString())
        assertEquals(AccumulationBaseRhythmDirection.FLAT, rhythm.blocks.single().direction)
        assertEquals(AccumulationBaseRhythmState.STEADY, rhythm.blocks.single().rangeState)
    }

}
