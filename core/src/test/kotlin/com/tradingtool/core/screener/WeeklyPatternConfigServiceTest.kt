package com.tradingtool.core.screener

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.File

class WeeklyPatternConfigServiceTest {

    private val configFile = File("weekly_pattern_config.json")
    private val mapper = jacksonObjectMapper()

    @AfterTest
    fun cleanupGeneratedConfig() {
        configFile.delete()
    }

    @Test
    fun `loadConfig clamps lookbackWeeks into 7 to 20`() {
        mapper.writerWithDefaultPrettyPrinter().writeValue(
            configFile,
            WeeklyPatternConfig(
                lookbackWeeks = 30,
                buyZoneLookbackWeeks = 25,
                minWeeksRequired = 6,
            ),
        )

        val config = WeeklyPatternConfigService().loadConfig()
        assertEquals(20, config.lookbackWeeks)
        assertEquals(20, config.buyZoneLookbackWeeks)
    }

    @Test
    fun `loadConfig aligns minWeeksRequired with effective lookback`() {
        mapper.writerWithDefaultPrettyPrinter().writeValue(
            configFile,
            WeeklyPatternConfig(
                lookbackWeeks = 5,
                buyZoneLookbackWeeks = 3,
                minWeeksRequired = 10,
            ),
        )

        val config = WeeklyPatternConfigService().loadConfig()
        assertEquals(7, config.lookbackWeeks)
        assertEquals(7, config.buyZoneLookbackWeeks)
        assertEquals(7, config.minWeeksRequired)
    }
}
