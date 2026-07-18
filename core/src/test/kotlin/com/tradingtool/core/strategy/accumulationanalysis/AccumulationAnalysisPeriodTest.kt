package com.tradingtool.core.strategy.accumulationanalysis

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AccumulationAnalysisPeriodTest {
    private val runDate = LocalDate.parse("2026-07-18")

    @Test
    fun `short replay periods use the requested trading evidence window`() {
        assertEquals(runDate, AccumulationAnalysisPeriod.ONE_DAY.fromDate(runDate))
        assertEquals(LocalDate.parse("2026-07-11"), AccumulationAnalysisPeriod.ONE_WEEK.fromDate(runDate))
    }
}
