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

    @Test
    fun `maps Supabase storage settings from the server configuration`() {
        val config = DropwizardConfig().apply {
            supabase.url = "https://project.supabase.co"
            supabase.serviceRoleKey = "local-service-role-key"
        }

        val appConfig = config.toAppConfig()

        assertEquals("https://project.supabase.co", appConfig.supabase.url)
        assertEquals(
            System.getenv("SUPABASE_SERVICE_ROLE_KEY")?.trim()?.takeIf { it.isNotEmpty() }
                ?: "local-service-role-key",
            appConfig.supabase.serviceRoleKey,
        )
    }

    @Test
    fun `unwraps a literal Redis URL accidentally wrapped in template syntax`() {
        val config = DropwizardConfig().apply {
            redis.url = "\${rediss://localhost:6380}"
        }

        assertEquals("rediss://localhost:6380", config.toAppConfig().redis.url)
    }
}
