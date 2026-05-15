package com.tradingtool.wyckoffcycle

import kotlin.test.Test
import kotlin.test.assertEquals

class WyckoffMarketCycleModuleTest {
    @Test
    fun returnsModuleName() {
        val module = WyckoffMarketCycleModule()

        assertEquals("Wyckoff Market Cycle", module.moduleName())
    }
}
