package com.tradingtool.resources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WeeklyCycleSuccessRequestTest {

    @Test
    fun `fromQuery applies defaults when params are missing`() {
        val request = WeeklyCycleSuccessRequest.fromQuery(
            universeRaw = null,
            weeksRaw = null,
            highLowPctRaw = null,
            rocPctRaw = null,
            prepareRaw = null,
        )

        assertNotNull(request)
        assertEquals(WeeklyCycleUniverse.BOTH, request.universe)
        assertEquals(8, request.weeks)
        assertEquals(10.0, request.highLowPct)
        assertEquals(2.0, request.rocPct)
        assertEquals(false, request.prepareMissingDaily)
    }

    @Test
    fun `fromQuery rejects out-of-range weeks and negative thresholds`() {
        assertNull(
            WeeklyCycleSuccessRequest.fromQuery(
                universeRaw = "BOTH",
                weeksRaw = 0,
                highLowPctRaw = 10.0,
                rocPctRaw = 2.0,
                prepareRaw = null,
            ),
        )
        assertNull(
            WeeklyCycleSuccessRequest.fromQuery(
                universeRaw = "BOTH",
                weeksRaw = 8,
                highLowPctRaw = -1.0,
                rocPctRaw = 2.0,
                prepareRaw = null,
            ),
        )
        assertNull(
            WeeklyCycleSuccessRequest.fromQuery(
                universeRaw = "BOTH",
                weeksRaw = 8,
                highLowPctRaw = 10.0,
                rocPctRaw = -1.0,
                prepareRaw = null,
            ),
        )
    }

    @Test
    fun `fromQuery parses supported universe aliases`() {
        assertEquals(
            WeeklyCycleUniverse.MIDCAP_250,
            WeeklyCycleSuccessRequest.fromQuery("MIDCAP250", 8, 10.0, 2.0, null)?.universe,
        )
        assertEquals(
            WeeklyCycleUniverse.SMALLCAP_250,
            WeeklyCycleSuccessRequest.fromQuery("NIFTY_SMALLCAP_250", 8, 10.0, 2.0, null)?.universe,
        )
        assertEquals(
            WeeklyCycleUniverse.BOTH,
            WeeklyCycleSuccessRequest.fromQuery("BOTH", 8, 10.0, 2.0, null)?.universe,
        )
        assertNull(WeeklyCycleSuccessRequest.fromQuery("UNKNOWN", 8, 10.0, 2.0, null))
    }

    @Test
    fun `fromQuery parses prepare flag`() {
        assertEquals(
            true,
            WeeklyCycleSuccessRequest.fromQuery("BOTH", 8, 10.0, 2.0, "true")?.prepareMissingDaily,
        )
        assertEquals(
            true,
            WeeklyCycleSuccessRequest.fromQuery("BOTH", 8, 10.0, 2.0, "1")?.prepareMissingDaily,
        )
        assertEquals(
            false,
            WeeklyCycleSuccessRequest.fromQuery("BOTH", 8, 10.0, 2.0, "false")?.prepareMissingDaily,
        )
    }
}
