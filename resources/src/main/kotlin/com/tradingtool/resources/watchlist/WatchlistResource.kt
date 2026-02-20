package com.tradingtool.resources.watchlist

import com.tradingtool.core.model.watchlist.*
import com.tradingtool.core.watchlist.service.WatchlistService
import com.tradingtool.core.watchlist.service.WatchlistServiceError
import com.tradingtool.core.watchlist.service.WatchlistServiceNotConfiguredError
import com.tradingtool.core.watchlist.service.WatchlistValidationError
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.CompletableFuture

@Path("/api/watchlist")
class WatchlistResource(
    private val watchlistService: WatchlistService,
) {
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    // Table access check
    @GET
    @Path("tables")
    @Produces(MediaType.APPLICATION_JSON)
    fun getTables(): CompletableFuture<Response> = ioScope.async {
        runService {
            val statuses = watchlistService.checkTablesAccess()
            Response.ok(json.encodeToString(statuses)).type(MediaType.APPLICATION_JSON).build()
        }
    }.asCompletableFuture()

    // Stock endpoints
    @POST
    @Path("stocks")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun createStock(body: String): CompletableFuture<Response> = ioScope.async {
        val payload = parseBody<CreateStockInput>(body)
            ?: return@async badRequest("Invalid request body for create stock")

        runService {
            val created = watchlistService.createStock(payload)
            Response.status(201).entity(json.encodeToString(created)).type(MediaType.APPLICATION_JSON).build()
        }
    }.asCompletableFuture()

    @GET
    @Path("stocks")
    @Produces(MediaType.APPLICATION_JSON)
    fun listStocks(@QueryParam("limit") limitParam: String?): CompletableFuture<Response> = ioScope.async {
        val limit = parseLimit(limitParam) ?: return@async badRequest("Invalid limit parameter")

        runService {
            val stocks = watchlistService.listStocks(limit)
            Response.ok(json.encodeToString(stocks)).type(MediaType.APPLICATION_JSON).build()
        }
    }.asCompletableFuture()

    @GET
    @Path("stocks/{stockId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getStockById(@PathParam("stockId") stockId: String): CompletableFuture<Response> = ioScope.async {
        val id = parseLongPathParam(stockId, "stockId")
            ?: return@async badRequest("Path parameter 'stockId' must be a valid integer")

        runNullableService(entityName = "Stock", entityId = id.toString()) {
            watchlistService.getStockById(id)
        }
    }.asCompletableFuture()

    @GET
    @Path("stocks/by-symbol/{nseSymbol}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getStockBySymbol(@PathParam("nseSymbol") nseSymbol: String?): CompletableFuture<Response> = ioScope.async {
        val symbol = nseSymbol?.trim() ?: return@async badRequest("Path parameter 'nseSymbol' is required")

        runNullableService(entityName = "Stock", entityId = symbol) {
            watchlistService.getStockByNseSymbol(symbol)
        }
    }.asCompletableFuture()

    @PATCH
    @Path("stocks/{stockId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun updateStock(@PathParam("stockId") stockId: String, body: String): CompletableFuture<Response> = ioScope.async {
        val id = parseLongPathParam(stockId, "stockId")
            ?: return@async badRequest("Path parameter 'stockId' must be a valid integer")

        val inputData = parseUpdateStockBody(body)
            ?: return@async badRequest("Invalid request body for update stock")

        runNullableService(entityName = "Stock", entityId = id.toString()) {
            watchlistService.updateStock(id, inputData)
        }
    }.asCompletableFuture()

    @DELETE
    @Path("stocks/{stockId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteStock(@PathParam("stockId") stockId: String): CompletableFuture<Response> = ioScope.async {
        val id = parseLongPathParam(stockId, "stockId")
            ?: return@async badRequest("Path parameter 'stockId' must be a valid integer")

        runService {
            val deleted = watchlistService.deleteStock(id)
            if (deleted) {
                Response.ok("""{"deleted":true}""").type(MediaType.APPLICATION_JSON).build()
            } else {
                notFound("Stock '$id' not found")
            }
        }
    }.asCompletableFuture()

    // Watchlist endpoints
    @POST
    @Path("lists")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun createWatchlist(body: String): CompletableFuture<Response> = ioScope.async {
        val payload = parseBody<CreateWatchlistInput>(body)
            ?: return@async badRequest("Invalid request body for create watchlist")

        runService {
            val created = watchlistService.createWatchlist(payload)
            Response.status(201).entity(json.encodeToString(created)).type(MediaType.APPLICATION_JSON).build()
        }
    }.asCompletableFuture()

    @GET
    @Path("lists")
    @Produces(MediaType.APPLICATION_JSON)
    fun listWatchlists(@QueryParam("limit") limitParam: String?): CompletableFuture<Response> = ioScope.async {
        val limit = parseLimit(limitParam) ?: return@async badRequest("Invalid limit parameter")

        runService {
            val watchlists = watchlistService.listWatchlists(limit)
            Response.ok(json.encodeToString(watchlists)).type(MediaType.APPLICATION_JSON).build()
        }
    }.asCompletableFuture()

    @GET
    @Path("lists/{watchlistId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getWatchlistById(@PathParam("watchlistId") watchlistId: String): CompletableFuture<Response> = ioScope.async {
        val id = parseLongPathParam(watchlistId, "watchlistId")
            ?: return@async badRequest("Path parameter 'watchlistId' must be a valid integer")

        runNullableService(entityName = "Watchlist", entityId = id.toString()) {
            watchlistService.getWatchlistById(id)
        }
    }.asCompletableFuture()

    @GET
    @Path("lists/by-name/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getWatchlistByName(@PathParam("name") name: String?): CompletableFuture<Response> = ioScope.async {
        val watchlistName = name?.trim() ?: return@async badRequest("Path parameter 'name' is required")

        runNullableService(entityName = "Watchlist", entityId = watchlistName) {
            watchlistService.getWatchlistByName(watchlistName)
        }
    }.asCompletableFuture()

    @PATCH
    @Path("lists/{watchlistId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun updateWatchlist(@PathParam("watchlistId") watchlistId: String, body: String): CompletableFuture<Response> = ioScope.async {
        val id = parseLongPathParam(watchlistId, "watchlistId")
            ?: return@async badRequest("Path parameter 'watchlistId' must be a valid integer")

        val inputData = parseUpdateWatchlistBody(body)
            ?: return@async badRequest("Invalid request body for update watchlist")

        runNullableService(entityName = "Watchlist", entityId = id.toString()) {
            watchlistService.updateWatchlist(id, inputData)
        }
    }.asCompletableFuture()

    @DELETE
    @Path("lists/{watchlistId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteWatchlist(@PathParam("watchlistId") watchlistId: String): CompletableFuture<Response> = ioScope.async {
        val id = parseLongPathParam(watchlistId, "watchlistId")
            ?: return@async badRequest("Path parameter 'watchlistId' must be a valid integer")

        runService {
            val deleted = watchlistService.deleteWatchlist(id)
            if (deleted) {
                Response.ok("""{"deleted":true}""").type(MediaType.APPLICATION_JSON).build()
            } else {
                notFound("Watchlist '$id' not found")
            }
        }
    }.asCompletableFuture()

    // Watchlist-Stock mapping endpoints
    @POST
    @Path("items")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun createWatchlistStock(body: String): CompletableFuture<Response> = ioScope.async {
        val payload = parseBody<CreateWatchlistStockInput>(body)
            ?: return@async badRequest("Invalid request body for create watchlist stock mapping")

        runService {
            val created = watchlistService.createWatchlistStock(payload)
            Response.status(201).entity(json.encodeToString(created)).type(MediaType.APPLICATION_JSON).build()
        }
    }.asCompletableFuture()

    @GET
    @Path("items/{watchlistId}/{stockId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getWatchlistStock(
        @PathParam("watchlistId") watchlistId: String,
        @PathParam("stockId") stockId: String,
    ): CompletableFuture<Response> = ioScope.async {
        val wid = parseLongPathParam(watchlistId, "watchlistId")
            ?: return@async badRequest("Path parameter 'watchlistId' must be a valid integer")
        val sid = parseLongPathParam(stockId, "stockId")
            ?: return@async badRequest("Path parameter 'stockId' must be a valid integer")

        runNullableService(entityName = "Mapping", entityId = "$wid:$sid") {
            watchlistService.getWatchlistStock(wid, sid)
        }
    }.asCompletableFuture()

    @GET
    @Path("lists/{watchlistId}/items")
    @Produces(MediaType.APPLICATION_JSON)
    fun listWatchlistItems(@PathParam("watchlistId") watchlistId: String): CompletableFuture<Response> = ioScope.async {
        val wid = parseLongPathParam(watchlistId, "watchlistId")
            ?: return@async badRequest("Path parameter 'watchlistId' must be a valid integer")

        runService {
            val mappings = watchlistService.listStocksForWatchlist(wid)
            Response.ok(json.encodeToString(mappings)).type(MediaType.APPLICATION_JSON).build()
        }
    }.asCompletableFuture()

    @PATCH
    @Path("items/{watchlistId}/{stockId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun updateWatchlistStock(
        @PathParam("watchlistId") watchlistId: String,
        @PathParam("stockId") stockId: String,
        body: String,
    ): CompletableFuture<Response> = ioScope.async {
        val wid = parseLongPathParam(watchlistId, "watchlistId")
            ?: return@async badRequest("Path parameter 'watchlistId' must be a valid integer")
        val sid = parseLongPathParam(stockId, "stockId")
            ?: return@async badRequest("Path parameter 'stockId' must be a valid integer")

        val inputData = parseUpdateWatchlistStockBody(body)
            ?: return@async badRequest("Invalid request body for update watchlist stock mapping")

        runNullableService(entityName = "Mapping", entityId = "$wid:$sid") {
            watchlistService.updateWatchlistStock(wid, sid, inputData)
        }
    }.asCompletableFuture()

    @DELETE
    @Path("items/{watchlistId}/{stockId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteWatchlistStock(
        @PathParam("watchlistId") watchlistId: String,
        @PathParam("stockId") stockId: String,
    ): CompletableFuture<Response> = ioScope.async {
        val wid = parseLongPathParam(watchlistId, "watchlistId")
            ?: return@async badRequest("Path parameter 'watchlistId' must be a valid integer")
        val sid = parseLongPathParam(stockId, "stockId")
            ?: return@async badRequest("Path parameter 'stockId' must be a valid integer")

        runService {
            val deleted = watchlistService.deleteWatchlistStock(wid, sid)
            if (deleted) {
                Response.ok("""{"deleted":true}""").type(MediaType.APPLICATION_JSON).build()
            } else {
                notFound("Mapping '$wid:$sid' not found")
            }
        }
    }.asCompletableFuture()

    // Helper functions for PATCH field detection
    private fun parseUpdateStockBody(body: String): UpdateStockInput? {
        val bodyObject = runCatching {
            json.parseToJsonElement(body) as? JsonObject
        }.getOrNull() ?: return null

        val payload = runCatching {
            json.decodeFromJsonElement(UpdateStockPayload.serializer(), bodyObject)
        }.getOrNull() ?: return null

        val fieldsToUpdate = mutableSetOf<StockUpdateField>()
        if (bodyObject.containsKey("company_name")) fieldsToUpdate.add(StockUpdateField.COMPANY_NAME)
        if (bodyObject.containsKey("groww_symbol")) fieldsToUpdate.add(StockUpdateField.GROWW_SYMBOL)
        if (bodyObject.containsKey("kite_symbol")) fieldsToUpdate.add(StockUpdateField.KITE_SYMBOL)
        if (bodyObject.containsKey("description")) fieldsToUpdate.add(StockUpdateField.DESCRIPTION)
        if (bodyObject.containsKey("rating")) fieldsToUpdate.add(StockUpdateField.RATING)
        if (bodyObject.containsKey("tags")) fieldsToUpdate.add(StockUpdateField.TAGS)

        return UpdateStockInput(
            fieldsToUpdate = fieldsToUpdate,
            companyName = payload.companyName,
            growwSymbol = payload.growwSymbol,
            kiteSymbol = payload.kiteSymbol,
            description = payload.description,
            rating = payload.rating,
            tags = payload.tags,
        )
    }

    private fun parseUpdateWatchlistBody(body: String): UpdateWatchlistInput? {
        val bodyObject = runCatching {
            json.parseToJsonElement(body) as? JsonObject
        }.getOrNull() ?: return null

        val payload = runCatching {
            json.decodeFromJsonElement(UpdateWatchlistPayload.serializer(), bodyObject)
        }.getOrNull() ?: return null

        val fieldsToUpdate = mutableSetOf<WatchlistUpdateField>()
        if (bodyObject.containsKey("name")) fieldsToUpdate.add(WatchlistUpdateField.NAME)
        if (bodyObject.containsKey("description")) fieldsToUpdate.add(WatchlistUpdateField.DESCRIPTION)

        return UpdateWatchlistInput(
            fieldsToUpdate = fieldsToUpdate,
            name = payload.name,
            description = payload.description,
        )
    }

    private fun parseUpdateWatchlistStockBody(body: String): UpdateWatchlistStockInput? {
        val bodyObject = runCatching {
            json.parseToJsonElement(body) as? JsonObject
        }.getOrNull() ?: return null

        val payload = runCatching {
            json.decodeFromJsonElement(UpdateWatchlistStockPayload.serializer(), bodyObject)
        }.getOrNull() ?: return null

        val fieldsToUpdate = mutableSetOf<WatchlistStockUpdateField>()
        if (bodyObject.containsKey("notes")) fieldsToUpdate.add(WatchlistStockUpdateField.NOTES)

        return UpdateWatchlistStockInput(
            fieldsToUpdate = fieldsToUpdate,
            notes = payload.notes,
        )
    }

    // Parsing helpers
    private inline fun <reified T> parseBody(body: String): T? {
        return runCatching {
            json.decodeFromString<T>(body)
        }.getOrNull()
    }

    private fun parseLongPathParam(value: String, name: String): Long? {
        return value.trim().toLongOrNull()
    }

    private fun parseLimit(limitParam: String?): Int? {
        if (limitParam.isNullOrBlank()) return 200
        return limitParam.trim().toIntOrNull()
    }

    // Error handling wrappers
    private fun runService(operation: () -> Response): Response {
        return try {
            operation()
        } catch (e: WatchlistValidationError) {
            badRequest(e.message ?: "Validation failed")
        } catch (e: WatchlistServiceNotConfiguredError) {
            serviceUnavailable(e.message ?: "Watchlist service is not configured")
        } catch (e: WatchlistServiceError) {
            internalError(e.message ?: "Watchlist service error")
        }
    }

    private fun <T> runNullableService(entityName: String, entityId: String, operation: () -> T?): Response {
        return try {
            val result = operation()
            if (result == null) {
                notFound("$entityName '$entityId' not found")
            } else {
                Response.ok(json.encodeToString(result)).type(MediaType.APPLICATION_JSON).build()
            }
        } catch (e: WatchlistValidationError) {
            badRequest(e.message ?: "Validation failed")
        } catch (e: WatchlistServiceNotConfiguredError) {
            serviceUnavailable(e.message ?: "Watchlist service is not configured")
        } catch (e: WatchlistServiceError) {
            internalError(e.message ?: "Watchlist service error")
        }
    }

    // Response builders
    private fun badRequest(detail: String): Response {
        val payload = buildJsonObject { put("detail", detail) }
        return Response.status(400).entity(payload.toString()).type(MediaType.APPLICATION_JSON).build()
    }

    private fun notFound(detail: String): Response {
        val payload = buildJsonObject { put("detail", detail) }
        return Response.status(404).entity(payload.toString()).type(MediaType.APPLICATION_JSON).build()
    }

    private fun serviceUnavailable(detail: String): Response {
        val payload = buildJsonObject { put("detail", detail) }
        return Response.status(503).entity(payload.toString()).type(MediaType.APPLICATION_JSON).build()
    }

    private fun internalError(detail: String): Response {
        val payload = buildJsonObject { put("detail", detail) }
        return Response.status(500).entity(payload.toString()).type(MediaType.APPLICATION_JSON).build()
    }
}
