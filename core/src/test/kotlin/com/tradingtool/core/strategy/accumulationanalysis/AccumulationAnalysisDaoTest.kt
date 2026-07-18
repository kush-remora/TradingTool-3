package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.strategy.accumulationanalysis.dao.confirmationDatesFrom
import com.tradingtool.core.strategy.accumulationanalysis.dao.shapeMetricsFrom
import com.tradingtool.core.strategy.accumulationanalysis.dao.goldenFlatNodeFrom
import com.tradingtool.core.strategy.accumulationanalysis.dao.shapeChunksFrom
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
        val node = requireNotNull(goldenFlatNodeFrom("""{"goldenFlatNode":{"windowSessions":20,"startDate":"2026-05-13","endDate":"2026-06-09","metrics":{"curvature":0.001,"centerSlopePerTenSessions":0.1,"startSlopePerTenSessions":0.08,"endSlopePerTenSessions":0.12,"vertexPosition":null}}}"""))

        assertEquals(20, node.windowSessions)
        assertEquals("2026-06-09", node.endDate.toString())
    }

    @Test
    fun `reads the latest three chunk path`() {
        val chunks = shapeChunksFrom("""{"latestShapeChunks":[{"position":1,"startDate":"2026-04-14","endDate":"2026-05-11","shape":"FLAT","goldenFlat":false,"metrics":{"curvature":0.001,"centerSlopePerTenSessions":0.1,"startSlopePerTenSessions":0.08,"endSlopePerTenSessions":0.12,"vertexPosition":null}},{"position":2,"startDate":"2026-05-12","endDate":"2026-06-09","shape":"FLAT","goldenFlat":false,"metrics":{"curvature":0.001,"centerSlopePerTenSessions":0.1,"startSlopePerTenSessions":0.08,"endSlopePerTenSessions":0.12,"vertexPosition":null}},{"position":3,"startDate":"2026-06-10","endDate":"2026-07-07","shape":"FLAT_GOLDEN","goldenFlat":true,"metrics":{"curvature":0.001,"centerSlopePerTenSessions":0.1,"startSlopePerTenSessions":0.08,"endSlopePerTenSessions":0.12,"vertexPosition":null}}]}""")

        assertEquals(listOf(AccumulationShape.FLAT, AccumulationShape.FLAT, AccumulationShape.FLAT_GOLDEN), chunks.map(AccumulationShapeChunk::shape))
    }

}
