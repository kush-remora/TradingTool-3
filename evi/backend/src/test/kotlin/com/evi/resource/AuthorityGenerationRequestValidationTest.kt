package com.evi.resource

import com.evi.model.AuthorityGenerationRequest
import com.evi.model.AuthorityMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthorityGenerationRequestValidationTest {

    @Test
    fun `trims authority generation input`() {
        val validated = validateAuthorityGenerationRequest(
            AuthorityGenerationRequest(
                authorityMode = AuthorityMode.RESTRUCTURE,
                sourceDocumentContent = "  Keep this wording  ",
                sourceStructureJson = "  {\"blocks\": []}  ",
                userInstructions = "  Make the hierarchy clearer  ",
            ),
        )

        assertEquals("Keep this wording", validated.sourceDocumentContent)
        assertEquals("{\"blocks\": []}", validated.sourceStructureJson)
        assertEquals("Make the hierarchy clearer", validated.userInstructions)
    }

    @Test
    fun `requires an explicit authority mode`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validateAuthorityGenerationRequest(
                AuthorityGenerationRequest(sourceDocumentContent = "Source text"),
            )
        }

        assertEquals("authorityMode is required.", error.message)
    }
}
