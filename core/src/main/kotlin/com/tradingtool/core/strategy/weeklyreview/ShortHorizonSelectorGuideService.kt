package com.tradingtool.core.strategy.weeklyreview

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class ShortHorizonSelectorGuideService @Inject constructor() {
    private val objectMapper = ObjectMapper()

    fun loadTabOneGuide(): JsonNode {
        val resource = javaClass.classLoader.getResourceAsStream(GUIDE_RESOURCE_NAME)
            ?: error("Short-horizon Tab 1 guide resource is missing: $GUIDE_RESOURCE_NAME")

        return resource.use { input -> objectMapper.readTree(input) }
    }

    private companion object {
        const val GUIDE_RESOURCE_NAME = "short_horizon_selector/tab_one_guide.json"
    }
}
