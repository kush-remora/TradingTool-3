package com.evi.resource

import com.evi.model.VisualBookLayoutRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VisualBookRequestValidationTest {

    @Test
    fun `trims valid visual book input`() {
        val validated = validateVisualBookLayoutRequest(
            VisualBookLayoutRequest(
                bookTitle = "  The Quiet Future  ",
                objective = "  Explain regenerative cities  ",
                pageText = "  A slower city makes more room for life.  ",
            ),
        )

        assertEquals("The Quiet Future", validated.bookTitle)
        assertEquals("Explain regenerative cities", validated.objective)
        assertEquals("A slower city makes more room for life.", validated.pageText)
    }

    @Test
    fun `rejects a blank first page`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validateVisualBookLayoutRequest(VisualBookLayoutRequest(pageText = "   "))
        }

        assertEquals("pageText is required.", error.message)
    }
}
