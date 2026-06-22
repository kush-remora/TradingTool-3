package com.tradingtool.core.volumeshocker.groww

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MarketCapCategoryTest {

    @Test
    fun `classifies approximate market-cap boundaries`() {
        assertEquals(MarketCapCategory.LARGE, MarketCapCategory.fromMarketCap(BigDecimal("105000")))
        assertEquals(MarketCapCategory.MID, MarketCapCategory.fromMarketCap(BigDecimal("104999.99")))
        assertEquals(MarketCapCategory.MID, MarketCapCategory.fromMarketCap(BigDecimal("34700")))
        assertEquals(MarketCapCategory.SMALL, MarketCapCategory.fromMarketCap(BigDecimal("34699.99")))
    }
}
