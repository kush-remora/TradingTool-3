package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.strategy.accumulationanalysis.dao.confirmationDatesFrom
import com.tradingtool.core.strategy.accumulationanalysis.dao.shapeMetricsFrom
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

}
