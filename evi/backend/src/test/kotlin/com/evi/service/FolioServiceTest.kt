package com.evi.service

import com.evi.model.CreateFolioRequest
import com.evi.model.FolioSummary
import com.evi.model.StoredFolio
import com.evi.model.StoredFolioPage
import com.evi.model.StoredFolioRecord
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FolioServiceTest {
    @Test
    fun `creates a UUID scoped object then stores uploaded folio metadata`() = runBlocking {
        val storage = RecordingStorage()
        val store = RecordingStore()
        val service = FolioService(storage, store)

        val result = service.create(
            CreateFolioRequest(
                title = "Cities",
                objective = "Explore better urban living.",
                theme = "Editorial",
                sourceFileName = "cities.pdf",
                sourceFileSizeBytes = 128,
            ),
            "%PDF-1.7".encodeToByteArray(),
        )

        assertEquals("uploaded", result.status)
        assertEquals(1, storage.uploadedPaths.size)
        assertTrue(storage.uploadedPaths.single().matches(Regex("folios/[0-9a-f-]{36}/cities-\\d{8}T\\d{6}Z\\.pdf")))
        assertEquals(storage.uploadedPaths.single(), store.createdFolio?.sourcePdfPath)
    }

    @Test
    fun `removes uploaded object when the database insert fails`() = runBlocking {
        val storage = RecordingStorage()
        val service = FolioService(storage, FailingStore())

        assertFailsWith<IllegalStateException> {
            service.create(
                CreateFolioRequest("Cities", "Objective", null, "cities.pdf", 128),
                "%PDF-1.7".encodeToByteArray(),
            )
        }

        assertEquals(1, storage.uploadedPaths.size)
        assertEquals(1, storage.deletedPaths.size)
        assertEquals(storage.uploadedPaths.single(), storage.deletedPaths.single())
    }

    @Test
    fun `extracts page text from PDF and updates page count when not cached`() = runBlocking {
        val pdfBytes = createSamplePdf(listOf("Hello from page 1", "Hello from page 2"))
        val folioId = UUID.randomUUID()
        val storage = RecordingStorage(mapOf("folios/$folioId/test.pdf" to pdfBytes))
        val store = RecordingStore(
            records = mutableMapOf(
                folioId to StoredFolioRecord(
                    id = folioId,
                    title = "Test Folio",
                    objective = "Testing page extraction",
                    theme = "Test",
                    sourcePdfPath = "folios/$folioId/test.pdf",
                    sourceFileName = "test.pdf",
                    sourceFileSizeBytes = pdfBytes.size.toLong(),
                    pageCount = null,
                    status = "uploaded",
                ),
            ),
        )
        val service = FolioService(storage, store)

        val page = service.getPage(folioId, 1)

        assertEquals("Hello from page 1", page.extractedText)
        assertEquals(1, page.pageNumber)
        assertEquals(2, page.totalPages)
        assertEquals("ready", page.status)
        assertEquals(2, store.updatedPageCounts[folioId])
    }

    @Test
    fun `returns cached page text when available`() = runBlocking {
        val folioId = UUID.randomUUID()
        val storage = RecordingStorage()
        val store = RecordingStore(
            records = mutableMapOf(
                folioId to StoredFolioRecord(
                    id = folioId,
                    title = "Cached Folio",
                    objective = "Cached test",
                    theme = "Test",
                    sourcePdfPath = "folios/$folioId/test.pdf",
                    sourceFileName = "test.pdf",
                    sourceFileSizeBytes = 100,
                    pageCount = 3,
                    status = "ready",
                ),
            ),
            pages = mutableMapOf(
                Pair(folioId, 2) to StoredFolioPage(
                    id = UUID.randomUUID(),
                    folioId = folioId,
                    pageNumber = 2,
                    extractedText = "Cached page 2 text",
                    status = "ready",
                ),
            ),
        )
        val service = FolioService(storage, store)

        val page = service.getPage(folioId, 2)

        assertEquals("Cached page 2 text", page.extractedText)
        assertEquals(2, page.pageNumber)
        assertEquals(3, page.totalPages)
        assertEquals(0, storage.downloadedPaths.size)
    }

    @Test
    fun `rejects out of bounds page request`() {
        runBlocking {
            val folioId = UUID.randomUUID()
            val storage = RecordingStorage()
            val store = RecordingStore(
                records = mutableMapOf(
                    folioId to StoredFolioRecord(
                        id = folioId,
                        title = "Bounded Folio",
                        objective = "Bounds test",
                        theme = "Test",
                        sourcePdfPath = "folios/$folioId/test.pdf",
                        sourceFileName = "test.pdf",
                        sourceFileSizeBytes = 100,
                        pageCount = 2,
                        status = "ready",
                    ),
                ),
            )
            val service = FolioService(storage, store)

            assertFailsWith<FolioPageOutOfBoundsException> {
                service.getPage(folioId, 5)
            }
        }
    }

    private fun createSamplePdf(pagesText: List<String>): ByteArray {
        val doc = PDDocument()
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        for (text in pagesText) {
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(font, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText(text)
                cs.endText()
            }
        }
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private class RecordingStorage(
        private val seedData: Map<String, ByteArray> = emptyMap(),
    ) : FolioStorage {
        val uploadedPaths = mutableListOf<String>()
        val deletedPaths = mutableListOf<String>()
        val downloadedPaths = mutableListOf<String>()
        private val data = seedData.toMutableMap()

        override suspend fun uploadPdf(path: String, pdfBytes: ByteArray) {
            uploadedPaths += path
            data[path] = pdfBytes
        }

        override suspend fun downloadPdf(path: String): ByteArray {
            downloadedPaths += path
            return data[path] ?: error("Path not found: $path")
        }

        override suspend fun delete(path: String) {
            deletedPaths += path
            data.remove(path)
        }
    }

    private class RecordingStore(
        val records: MutableMap<UUID, StoredFolioRecord> = mutableMapOf(),
        val pages: MutableMap<Pair<UUID, Int>, StoredFolioPage> = mutableMapOf(),
    ) : FolioStore {
        var createdFolio: StoredFolio? = null
        val updatedPageCounts = mutableMapOf<UUID, Int>()

        override suspend fun create(folio: StoredFolio): FolioSummary {
            createdFolio = folio
            return FolioSummary(
                id = folio.id.toString(),
                title = folio.title,
                objective = folio.objective,
                theme = folio.theme,
                sourceFileName = folio.sourceFileName,
                sourceFileSizeBytes = folio.sourceFileSizeBytes,
                status = "uploaded",
                createdAt = "2026-11-05T00:00:00Z",
                updatedAt = "2026-11-05T00:00:00Z",
            )
        }

        override suspend fun listRecent(): List<FolioSummary> = emptyList()

        override suspend fun findById(id: UUID): StoredFolioRecord? = records[id]

        override suspend fun updatePageCount(id: UUID, pageCount: Int) {
            updatedPageCounts[id] = pageCount
            val existing = records[id]
            if (existing != null) {
                records[id] = existing.copy(pageCount = pageCount)
            }
        }

        override suspend fun findPage(folioId: UUID, pageNumber: Int): StoredFolioPage? =
            pages[Pair(folioId, pageNumber)]

        override suspend fun savePageText(
            folioId: UUID,
            pageNumber: Int,
            text: String,
            status: String,
        ): StoredFolioPage {
            val page = StoredFolioPage(
                id = UUID.randomUUID(),
                folioId = folioId,
                pageNumber = pageNumber,
                extractedText = text,
                status = status,
            )
            pages[Pair(folioId, pageNumber)] = page
            return page
        }
    }

    private class FailingStore : FolioStore {
        override suspend fun create(folio: StoredFolio): FolioSummary = error("DB unavailable")
        override suspend fun listRecent(): List<FolioSummary> = emptyList()
        override suspend fun findById(id: UUID): StoredFolioRecord? = null
        override suspend fun updatePageCount(id: UUID, pageCount: Int) {}
        override suspend fun findPage(folioId: UUID, pageNumber: Int): StoredFolioPage? = null
        override suspend fun savePageText(folioId: UUID, pageNumber: Int, text: String, status: String): StoredFolioPage =
            error("DB unavailable")
    }
}
