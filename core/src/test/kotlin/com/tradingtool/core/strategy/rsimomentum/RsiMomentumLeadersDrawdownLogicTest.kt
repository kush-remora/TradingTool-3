package com.tradingtool.core.strategy.rsimomentum

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RsiMomentumLeadersDrawdownLogicTest {

    private val thresholds = listOf(20, 30, 40, 50, 60)

    @Test
    fun `calculate drawdown returns null when values are invalid`() {
        assertNull(calculateDrawdownPct(null, 100.0))
        assertNull(calculateDrawdownPct(0.0, 100.0))
        assertNull(calculateDrawdownPct(100.0, null))
    }

    @Test
    fun `calculate drawdown matches expected formula`() {
        val drawdown = calculateDrawdownPct(200.0, 150.0)
        assertEquals(-25.0, drawdown)
    }

    @Test
    fun `bucket flags respect exact threshold boundaries`() {
        val at20 = buildDrawdownBucketFlags(-20.0, thresholds)
        assertTrue(at20.atLeast20Pct)
        assertFalse(at20.atLeast30Pct)

        val at40 = buildDrawdownBucketFlags(-40.0, thresholds)
        assertTrue(at40.atLeast20Pct)
        assertTrue(at40.atLeast30Pct)
        assertTrue(at40.atLeast40Pct)
        assertFalse(at40.atLeast50Pct)
    }

    @Test
    fun `bucket summary counts each threshold correctly`() {
        val summary = summarizeDrawdownBuckets(
            drawdowns = listOf(-20.0, -30.0, -40.0, -50.0, -60.0, -15.0, null),
            thresholds = thresholds,
        )

        assertEquals(5, summary.atLeast20Pct)
        assertEquals(4, summary.atLeast30Pct)
        assertEquals(3, summary.atLeast40Pct)
        assertEquals(2, summary.atLeast50Pct)
        assertEquals(1, summary.atLeast60Pct)
    }
}
