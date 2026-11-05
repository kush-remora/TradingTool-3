package com.evi.resource

import jakarta.ws.rs.core.MediaType
import org.glassfish.jersey.media.multipart.FormDataBodyPart
import org.glassfish.jersey.media.multipart.FormDataContentDisposition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FolioResourceValidationTest {
    @Test
    fun `accepts a PDF upload and normalizes form metadata`() {
        val request = validateCreateFolioRequest(
            title = "  Urban futures  ",
            objective = "  Explain walkable cities. ",
            theme = "  Editorial ",
            fileMetadata = pdfPart("cities.pdf"),
            pdfBytes = "%PDF-1.7".encodeToByteArray(),
        )

        assertEquals("Urban futures", request.title)
        assertEquals("Explain walkable cities.", request.objective)
        assertEquals("Editorial", request.theme)
        assertEquals("cities.pdf", request.sourceFileName)
    }

    @Test
    fun `rejects a non PDF payload`() {
        assertFailsWith<IllegalArgumentException> {
            validateCreateFolioRequest(
                title = "Cities",
                objective = "Explain walkable cities.",
                theme = null,
                fileMetadata = pdfPart("cities.pdf"),
                pdfBytes = "plain text".encodeToByteArray(),
            )
        }
    }

    private fun pdfPart(fileName: String): FormDataBodyPart = FormDataBodyPart(
        MediaType.valueOf("application/pdf"),
    ).apply {
        formDataContentDisposition = FormDataContentDisposition.name("file").fileName(fileName).build()
    }
}
