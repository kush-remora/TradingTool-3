package com.evi.service

import com.evi.client.EviHttpClient
import com.evi.client.EviHttpRequestData
import com.evi.database.EviJdbiHandler
import com.evi.database.FolioReadDao
import com.evi.database.FolioWriteDao
import com.evi.model.CreateFolioRequest
import com.evi.model.FolioPageSummary
import com.evi.model.FolioSummary
import com.evi.model.StoredFolio
import com.evi.model.StoredFolioPage
import com.evi.model.StoredFolioRecord
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException

private const val FOLIO_BUCKET = "ey-books"
private const val FOLIO_LIST_LIMIT = 100
private val STORAGE_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(60)
private val STORAGE_FILE_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyyMMdd'T'HHmmss'Z'")
    .withZone(ZoneOffset.UTC)

interface FolioStorage {
    suspend fun uploadPdf(path: String, pdfBytes: ByteArray)
    suspend fun downloadPdf(path: String): ByteArray
    suspend fun delete(path: String)
}

interface FolioStore {
    suspend fun create(folio: StoredFolio): FolioSummary
    suspend fun listRecent(): List<FolioSummary>
    suspend fun findById(id: UUID): StoredFolioRecord?
    suspend fun updatePageCount(id: UUID, pageCount: Int)
    suspend fun findPage(folioId: UUID, pageNumber: Int): StoredFolioPage?
    suspend fun savePageText(folioId: UUID, pageNumber: Int, text: String, status: String): StoredFolioPage
}

class FolioStorageNotConfiguredException : IllegalStateException("PDF storage is not configured.")
class FolioNotFoundException(val id: UUID) : IllegalArgumentException("Folio $id not found.")
class FolioPageOutOfBoundsException(val pageNumber: Int, val totalPages: Int) :
    IllegalArgumentException("Page $pageNumber is out of bounds (1..$totalPages).")
class FolioExtractionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class FolioService(
    private val storage: FolioStorage,
    private val store: FolioStore,
) {
    suspend fun create(request: CreateFolioRequest, pdfBytes: ByteArray): FolioSummary {
        val folioId = UUID.randomUUID()
        val sourcePdfPath = "folios/$folioId/${storageFileName(request.title)}"
        val folio = StoredFolio(
            id = folioId,
            title = request.title,
            objective = request.objective,
            theme = request.theme.orEmpty(),
            sourcePdfPath = sourcePdfPath,
            sourceFileName = request.sourceFileName,
            sourceFileSizeBytes = request.sourceFileSizeBytes,
        )

        var uploaded = false
        try {
            storage.uploadPdf(sourcePdfPath, pdfBytes)
            uploaded = true
            return store.create(folio)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (uploaded) {
                runCatching { storage.delete(sourcePdfPath) }
            }
            throw error
        }
    }

    suspend fun listRecent(): List<FolioSummary> = store.listRecent()

    suspend fun getPage(folioId: UUID, pageNumber: Int): FolioPageSummary {
        require(pageNumber >= 1) { "Page number must be at least 1." }

        val folio = store.findById(folioId) ?: throw FolioNotFoundException(folioId)

        if (folio.pageCount != null && pageNumber > folio.pageCount) {
            throw FolioPageOutOfBoundsException(pageNumber, folio.pageCount)
        }

        val cachedPage = store.findPage(folioId, pageNumber)
        if (cachedPage != null && cachedPage.extractedText != null && folio.pageCount != null) {
            return FolioPageSummary(
                folioId = folioId.toString(),
                pageNumber = pageNumber,
                totalPages = folio.pageCount,
                extractedText = cachedPage.extractedText,
                status = cachedPage.status,
            )
        }

        val sourcePdfPath = folio.sourcePdfPath
        check(!sourcePdfPath.isNullOrBlank()) { "Folio source PDF path is not set." }

        val pdfBytes = storage.downloadPdf(sourcePdfPath)

        val (totalPages, pageText) = try {
            Loader.loadPDF(pdfBytes).use { document ->
                val total = document.numberOfPages
                if (pageNumber > total) {
                    throw FolioPageOutOfBoundsException(pageNumber, total)
                }
                val stripper = PDFTextStripper().apply {
                    startPage = pageNumber
                    endPage = pageNumber
                }
                val text = stripper.getText(document).trim()
                total to text
            }
        } catch (error: FolioPageOutOfBoundsException) {
            throw error
        } catch (error: Exception) {
            throw FolioExtractionException("Failed to extract text from page $pageNumber: ${error.message}", error)
        }

        if (folio.pageCount == null || folio.pageCount != totalPages) {
            store.updatePageCount(folioId, totalPages)
        }

        val savedPage = store.savePageText(folioId, pageNumber, pageText, "ready")

        return FolioPageSummary(
            folioId = folioId.toString(),
            pageNumber = pageNumber,
            totalPages = totalPages,
            extractedText = savedPage.extractedText.orEmpty(),
            status = savedPage.status,
        )
    }

    suspend fun renderPage(folioId: UUID, pageNumber: Int): ByteArray {
        require(pageNumber >= 1) { "Page number must be at least 1." }

        val folio = store.findById(folioId) ?: throw FolioNotFoundException(folioId)
        val sourcePdfPath = folio.sourcePdfPath
        check(!sourcePdfPath.isNullOrBlank()) { "Folio source PDF path is not set." }

        val pdfBytes = storage.downloadPdf(sourcePdfPath)
        try {
            Loader.loadPDF(pdfBytes).use { document ->
                if (pageNumber > document.numberOfPages) {
                    throw FolioPageOutOfBoundsException(pageNumber, document.numberOfPages)
                }
                val image = PDFRenderer(document).renderImageWithDPI(
                    pageNumber - 1,
                    144f,
                    ImageType.RGB,
                )
                return ByteArrayOutputStream().use { output ->
                    check(ImageIO.write(image, "PNG", output)) { "Unable to encode rendered PDF page." }
                    output.toByteArray()
                }
            }
        } catch (error: FolioPageOutOfBoundsException) {
            throw error
        } catch (error: Exception) {
            throw FolioExtractionException("Failed to render page $pageNumber: ${error.message}", error)
        }
    }

    private fun storageFileName(title: String): String {
        val titleSlug = title
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(80)
            .ifBlank { "folio" }
        val timestamp = STORAGE_FILE_TIMESTAMP_FORMAT.format(Instant.now())
        return "$titleSlug-$timestamp.pdf"
    }
}

class SupabaseFolioStorage(
    private val projectUrl: String,
    private val serviceRoleKey: String,
    private val httpClient: EviHttpClient,
) : FolioStorage {
    override suspend fun uploadPdf(path: String, pdfBytes: ByteArray) {
        val response = httpClient.execute(
            EviHttpRequestData(
                method = "POST",
                uri = objectUri(path),
                headers = storageHeaders("application/pdf") + ("x-upsert" to "true"),
                body = pdfBytes,
                timeout = STORAGE_REQUEST_TIMEOUT,
            ),
        )
        check(response.statusCode in 200..299) { "Unable to upload PDF to storage." }
    }

    override suspend fun downloadPdf(path: String): ByteArray {
        val response = httpClient.executeBytes(
            EviHttpRequestData(
                method = "GET",
                uri = objectUri(path),
                headers = storageHeaders(),
                timeout = STORAGE_REQUEST_TIMEOUT,
            ),
        )
        check(response.statusCode in 200..299) {
            "Unable to download PDF from storage: status ${response.statusCode}."
        }
        return response.body
    }

    override suspend fun delete(path: String) {
        val body = "{\"prefixes\":[\"$path\"]}".toByteArray(StandardCharsets.UTF_8)
        val response = httpClient.execute(
            EviHttpRequestData(
                method = "DELETE",
                uri = URI.create("${normalizedProjectUrl()}/storage/v1/object/$FOLIO_BUCKET"),
                headers = storageHeaders("application/json"),
                body = body,
                timeout = STORAGE_REQUEST_TIMEOUT,
            ),
        )
        check(response.statusCode in 200..299) { "Unable to remove incomplete PDF upload." }
    }

    private fun objectUri(path: String): URI = URI.create(
        "${normalizedProjectUrl()}/storage/v1/object/$FOLIO_BUCKET/$path",
    )

    private fun storageHeaders(contentType: String? = null): Map<String, String> {
        val key = serviceRoleKey.trim()
        check(key.isNotEmpty()) { throw FolioStorageNotConfiguredException() }
        val headers = mutableMapOf(
            "Authorization" to "Bearer $key",
            "apikey" to key,
        )
        if (contentType != null) {
            headers["Content-Type"] = contentType
        }
        return headers
    }

    private fun normalizedProjectUrl(): String {
        val normalized = projectUrl.trim().removeSuffix("/")
        check(normalized.isNotEmpty()) { throw FolioStorageNotConfiguredException() }
        return normalized
    }
}

typealias FolioJdbiHandler = EviJdbiHandler<FolioReadDao, FolioWriteDao>

class JdbiFolioStore(
    private val handler: FolioJdbiHandler,
) : FolioStore {
    override suspend fun create(folio: StoredFolio): FolioSummary =
        handler.write { writeDao -> writeDao.insert(folio) }

    override suspend fun listRecent(): List<FolioSummary> =
        handler.read { readDao -> readDao.listRecent(FOLIO_LIST_LIMIT) }

    override suspend fun findById(id: UUID): StoredFolioRecord? =
        handler.read { readDao -> readDao.findById(id) }

    override suspend fun updatePageCount(id: UUID, pageCount: Int) {
        handler.write { writeDao -> writeDao.updatePageCount(id, pageCount) }
    }

    override suspend fun findPage(folioId: UUID, pageNumber: Int): StoredFolioPage? =
        handler.read { readDao -> readDao.findPage(folioId, pageNumber) }

    override suspend fun savePageText(
        folioId: UUID,
        pageNumber: Int,
        text: String,
        status: String,
    ): StoredFolioPage = handler.write { writeDao ->
        writeDao.savePageText(folioId, pageNumber, text, status)
    }
}
