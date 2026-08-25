package com.tradingtool.core.strategy.weeklyreview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShortHorizonSelectorGuideServiceTest {
    @Test
    fun `loads the Tab 1 guide from the backend JSON resource`() {
        val guide = ShortHorizonSelectorGuideService().loadTabOneGuide()

        assertEquals("How to read Tab 1 · All Stocks", guide["title"].asText())
        assertTrue(guide["columns"].size() >= 8)
        assertEquals("Move now (Now 5D / Prior 5D / pace)", guide["columns"][0]["column"].asText())
        assertTrue(guide["importantNote"].asText().contains("Tab 2"))
    }
}
