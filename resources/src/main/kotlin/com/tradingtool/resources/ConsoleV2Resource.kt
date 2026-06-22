package com.tradingtool.resources

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.inject.Inject
import com.tradingtool.core.database.IndexConstituentJdbiHandler
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.indexconstituents.IndexConstituentKeys
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.watchlist.groww.GrowwWatchlistStock
import com.tradingtool.core.watchlist.groww.GrowwWatchlistSyncRequest
import com.tradingtool.core.watchlist.groww.JdbiGrowwWatchlistStockGateway
import com.tradingtool.core.watchlist.groww.KiteInstrumentTokenResolver
import com.tradingtool.core.watchlist.groww.StringGrowwWatchlistAdapter
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.glassfish.jersey.media.multipart.FormDataBodyPart
import org.glassfish.jersey.media.multipart.FormDataParam
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

data class GrowwWatchlistImportRowSkip(
    val symbol: String,
    val reason: String,
)

data class GrowwWatchlistImportResponse(
    val fetchedCount: Int,
    val syncedCount: Int,
    val skippedCount: Int,
    val skippedSample: List<GrowwWatchlistImportRowSkip>,
)

@Path("/api/console/v2")
@Produces(MediaType.APPLICATION_JSON)
class ConsoleV2Resource @Inject constructor(
    private val resourceScope: ResourceScope,
    private val indexHandler: IndexConstituentJdbiHandler,
    private val kiteClient: KiteConnectClient,
    private val instrumentCache: InstrumentCache,
) {
    private val ioScope = resourceScope.ioScope
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @POST
    @Path("/groww/watchlist/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    fun importGrowwWatchlist(
        @FormDataParam("file") inputStream: InputStream?,
        @FormDataParam("file") fileMetadata: FormDataBodyPart?,
    ): CompletableFuture<Response> = ioScope.endpoint {
        if (inputStream == null || fileMetadata == null) {
            return@endpoint badRequest("JSON file is required (field name: file).")
        }

        val fileName = fileMetadata.contentDisposition?.fileName ?: "upload.json"
        val contentType = fileMetadata.mediaType?.toString() ?: "application/octet-stream"
        if (!fileName.lowercase().endsWith(".json") && contentType != MediaType.APPLICATION_JSON) {
            log.info("Groww import received fileName={} contentType={}", fileName, contentType)
        }

        val rawBytes = inputStream.readBytes()
        if (rawBytes.isEmpty()) {
            return@endpoint badRequest("Uploaded JSON file is empty.")
        }
        // Keep conservative: this is a single-user tool, but avoid accidental huge uploads.
        val maxBytes = 5 * 1024 * 1024 // 5 MB
        if (rawBytes.size > maxBytes) {
            return@endpoint badRequest("Uploaded file too large (${rawBytes.size} bytes). Max allowed is $maxBytes bytes.")
        }

        val json = rawBytes.toString(StandardCharsets.UTF_8).trim()
        if (json.isEmpty()) {
            return@endpoint badRequest("Uploaded JSON file contained no readable text.")
        }

        val instrumentTokenResolver = runCatching {
            if (!kiteClient.isAuthenticated) {
                null
            } else {
                KiteInstrumentTokenResolver(
                    kiteClient = kiteClient,
                    instrumentCache = instrumentCache,
                )
            }
        }.getOrElse { error ->
            log.warn("Kite token resolver unavailable; missing tokens will be skipped: {}", error.message)
            null
        }

        val source = StringGrowwWatchlistAdapter(
            json = json,
            objectMapper = objectMapper,
        )
        val stockGateway = JdbiGrowwWatchlistStockGateway(
            indexHandler = indexHandler,
            instrumentTokenResolver = instrumentTokenResolver,
        )

        val request = GrowwWatchlistSyncRequest()
        val rows: List<GrowwWatchlistStock> = source.fetchStocks(request)
        if (rows.isEmpty()) {
            return@endpoint badRequest("Uploaded Groww watchlist contained no NSE stocks; existing membership was not changed.")
        }

        val skipped = mutableListOf<GrowwWatchlistImportRowSkip>()
        var syncedCount = 0
        for (row in rows) {
            val result = stockGateway.upsertGrowwStock(row, IndexConstituentKeys.GROWW_WATCHLIST)
            if (result <= 0) {
                skipped += GrowwWatchlistImportRowSkip(
                    symbol = row.symbol,
                    reason = "Missing instrument token (and Kite resolver unavailable) or token could not be resolved.",
                )
            } else {
                syncedCount += result
            }
        }
        stockGateway.deactivateMissingGrowwStocks(
            indexKey = IndexConstituentKeys.GROWW_WATCHLIST,
            activeSymbols = rows.map { row -> row.symbol },
        )

        val response = GrowwWatchlistImportResponse(
            fetchedCount = rows.size,
            syncedCount = syncedCount,
            skippedCount = skipped.size,
            skippedSample = skipped.take(50),
        )
        ok(response)
    }
}
