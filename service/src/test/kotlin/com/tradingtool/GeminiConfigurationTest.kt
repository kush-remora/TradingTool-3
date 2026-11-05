package com.tradingtool

import com.tradingtool.config.DropwizardConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class GeminiConfigurationTest {

    @Test
    fun `uses the environment Gemini key when present otherwise the local config key`() {
        val config = DropwizardConfig().apply {
            gemini.apiKey = "local-gemini-key"
        }

        val configuredKey = config.toAppConfig().gemini.apiKey
        val environmentKey = System.getenv("GEMINI_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }

        assertEquals(environmentKey ?: "local-gemini-key", configuredKey)
    }
}
