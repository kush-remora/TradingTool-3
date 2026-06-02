package com.tradingtool.core.strategy.wyckoff.phase1

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WyckoffPhase1ConfigDefaultsTest {

    @Test
    fun `uses uniform 55 percent delivery threshold across cap buckets`() {
        val thresholds = WyckoffPhase1Config().trackA.deliveryThresholdByCap

        assertEquals(55.0, thresholds["MID_CAP"])
        assertEquals(55.0, thresholds["SMALL_CAP"])
        assertEquals(55.0, thresholds["MICRO_CAP"])
        assertEquals(55.0, thresholds["NANO_CAP"])
    }

    @Test
    fun `strict dma200 filter only caps upside`() {
        val dma200Proximity = WyckoffPhase1StrictFilterConfig().dma200Proximity

        assertEquals(-100.0, dma200Proximity.minDistancePct)
        assertEquals(2.0, dma200Proximity.maxDistancePct)
    }

    @Test
    fun `strict roc filter only caps upside`() {
        val roc20Proximity = WyckoffPhase1StrictFilterConfig().roc20Proximity

        assertEquals(-100.0, roc20Proximity.minPct)
        assertEquals(2.0, roc20Proximity.maxPct)
    }
}
