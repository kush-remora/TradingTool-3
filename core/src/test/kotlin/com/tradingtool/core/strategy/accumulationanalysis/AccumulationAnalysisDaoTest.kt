package com.tradingtool.core.strategy.accumulationanalysis

import com.tradingtool.core.strategy.accumulationanalysis.dao.confirmationDatesFrom
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

}
