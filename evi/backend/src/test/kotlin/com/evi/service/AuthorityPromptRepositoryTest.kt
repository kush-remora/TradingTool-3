package com.evi.service

import com.evi.model.AuthorityMode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthorityPromptRepositoryTest {
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val repository = AuthorityPromptRepository(objectMapper)

    @Test
    fun `loads the matching prompt contract for every authority mode`() {
        AuthorityMode.entries.forEach { mode ->
            val contract = repository.load(mode)

            assertTrue(contract.systemInstruction.contains("ACTIVE AUTHORITY MODE: ${mode.name}"))
            assertTrue(contract.userTemplate.contains("{{SOURCE_DOCUMENT_CONTENT}}"))
            assertEquals("${mode.name}", contract.responseSchema.path("properties").path("authority_mode").path("enum").path(0).asText())
        }
    }
}
