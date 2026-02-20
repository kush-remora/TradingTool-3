package com.tradingtool.watchlist

import com.google.inject.Inject
import com.tradingtool.core.model.watchlist.CreateStockInput
import com.tradingtool.core.model.watchlist.CreateWatchlistInput
import com.tradingtool.core.model.watchlist.CreateWatchlistStockInput
import com.tradingtool.core.model.watchlist.StockUpdateField
import com.tradingtool.core.model.watchlist.UpdateStockInput
import com.tradingtool.core.model.watchlist.UpdateStockPayload
import com.tradingtool.core.model.watchlist.UpdateWatchlistInput
import com.tradingtool.core.model.watchlist.UpdateWatchlistPayload
import com.tradingtool.core.model.watchlist.UpdateWatchlistStockInput
import com.tradingtool.core.model.watchlist.UpdateWatchlistStockPayload
import com.tradingtool.core.model.watchlist.WatchlistStockUpdateField
import com.tradingtool.core.model.watchlist.WatchlistUpdateField
import com.tradingtool.core.watchlist.service.WatchlistService
import com.tradingtool.core.watchlist.service.WatchlistServiceError
import com.tradingtool.core.watchlist.service.WatchlistServiceNotConfiguredError
import com.tradingtool.core.watchlist.service.WatchlistValidationError
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

private val requestJson: Json = Json { ignoreUnknownKeys = true }

class WatchlistResource(
    @Inject
    private val watchlistService: WatchlistService,
) {
    fun register(route: Route) {
        route.route("/api/watchlist") {
            get("tables") {
                handleGetTables(call)
            }

            post("stocks") {
                handleCreateStock(call)
            }
            get("stocks") {
                handleListStocks(call)
            }
            get("stocks/{stockId}") {
                handleGetStockById(call)
            }
            get("stocks/by-symbol/{nseSymbol}") {
                handleGetStockBySymbol(call)
            }
            patch("stocks/{stockId}") {
                handleUpdateStock(call)
            }
            delete("stocks/{stockId}") {
                handleDeleteStock(call)
            }

            post("lists") {
                handleCreateWatchlist(call)
            }
            get("lists") {
                handleListWatchlists(call)
            }
            get("lists/{watchlistId}") {
                handleGetWatchlistById(call)
            }
            get("lists/by-name/{name}") {
                handleGetWatchlistByName(call)
            }
            patch("lists/{watchlistId}") {
                handleUpdateWatchlist(call)
            }
            delete("lists/{watchlistId}") {
                handleDeleteWatchlist(call)
            }

            post("items") {
                handleCreateWatchlistStock(call)
            }
            get("items/{watchlistId}/{stockId}") {
                handleGetWatchlistStock(call)
            }
            get("lists/{watchlistId}/items") {
                handleListWatchlistItems(call)
            }
            patch("items/{watchlistId}/{stockId}") {
                handleUpdateWatchlistStock(call)
            }
            delete("items/{watchlistId}/{stockId}") {
                handleDeleteWatchlistStock(call)
            }
        }
    }

    private suspend fun handleGetTables(call: ApplicationCall) {
        val statuses = runService(
            call = call,
            action = "check table access",
            operation = { watchlistService.checkTablesAccess() },
        ) ?: return

        respondJson(call, HttpStatusCode.OK, statuses)
    }

    private suspend fun handleCreateStock(call: ApplicationCall) {
        val payload: CreateStockInput = parseJsonBody(
            call = call,
            errorContext = "create stock",
        ) ?: return

        val created = runService(
            call = call,
            action = "create stock",
            operation = { watchlistService.createStock(payload) },
        ) ?: return

        respondJson(call, HttpStatusCode.Created, created)
    }

    private suspend fun handleListStocks(call: ApplicationCall) {
        val limit: Int = parseLimit(call) ?: return
        val stocks = runService(
            call = call,
            action = "list stocks",
            operation = { watchlistService.listStocks(limit) },
        ) ?: return

        respondJson(call, HttpStatusCode.OK, stocks)
    }

    private suspend fun handleGetStockById(call: ApplicationCall) {
        val stockId: Long = parseLongPathParam(call, "stockId") ?: return
        val stockResult = runNullableService(
            call = call,
            action = "get stock by id",
            operation = { watchlistService.getStockById(stockId) },
        )
        if (stockResult.handledError) {
            return
        }
        val stock = stockResult.value

        if (stock == null) {
            respondDetail(
                call = call,
                status = HttpStatusCode.NotFound,
                detail = "Stock '$stockId' not found",
            )
            return
        }
        respondJson(call, HttpStatusCode.OK, stock)
    }

    private suspend fun handleGetStockBySymbol(call: ApplicationCall) {
        val nseSymbol: String = call.parameters["nseSymbol"]?.trim().orEmpty()
        if (nseSymbol.isEmpty()) {
            respondDetail(
                call = call,
                status = HttpStatusCode.BadRequest,
                detail = "Path parameter 'nseSymbol' is required.",
            )
            return
        }

        val stockResult = runNullableService(
            call = call,
            action = "get stock by symbol",
            operation = { watchlistService.getStockByNseSymbol(nseSymbol) },
        )
        if (stockResult.handledError) {
            return
        }
        val stock = stockResult.value

        if (stock == null) {
            respondDetail(
                call = call,
                status = HttpStatusCode.NotFound,
                detail = "Stock '$nseSymbol' not found",
            )
            return
        }
        respondJson(call, HttpStatusCode.OK, stock)
    }

    private suspend fun handleUpdateStock(call: ApplicationCall) {
        val stockId: Long = parseLongPathParam(call, "stockId") ?: return
        val inputData: UpdateStockInput = parseUpdateStockBody(call) ?: return

        val updatedResult = runNullableService(
            call = call,
            action = "update stock",
            operation = { watchlistService.updateStock(stockId, inputData) },
        )
        if (updatedResult.handledError) {
            return
        }
        val updated = updatedResult.value

        if (updated == null) {
            respondDetail(
                call = call,
                status = HttpStatusCode.NotFound,
                detail = "Stock '$stockId' not found",
            )
            return
        }
        respondJson(call, HttpStatusCode.OK, updated)
    }

    private suspend fun handleDeleteStock(call: ApplicationCall) {
        val stockId: Long = parseLongPathParam(call, "stockId") ?: return
        val deleted = runService(
            call = call,
            action = "delete stock",
            operation = { watchlistService.deleteStock(stockId) },
        ) ?: return

        if (deleted.not()) {
            respondDetail(
                call = call,
                status = HttpStatusCode.NotFound,
                detail = "Stock '$stockId' not found",
            )
            return
        }
        respondDeleted(call)
    }

    private suspend fun handleCreateWatchlist(call: ApplicationCall) {
        val payload: CreateWatchlistInput = parseJsonBody(
            call = call,
            errorContext = "create watchlist",
        ) ?: return

        val created = runService(
            call = call,
            action = "create watchlist",
            operation = { watchlistService.createWatchlist(payload) },
        ) ?: return

        respondJson(call, HttpStatusCode.Created, created)
    }

    private suspend fun handleListWatchlists(call: ApplicationCall) {
        val limit: Int = parseLimit(call) ?: return
        val watchlists = runService(
            call = call,
            action = "list watchlists",
            operation = { watchlistService.listWatchlists(limit) },
        ) ?: return

        respondJson(call, HttpStatusCode.OK, watchlists)
    }

    private suspend fun handleGetWatchlistById(call: ApplicationCall) {
        val watchlistId: Long = parseLongPathParam(call, "watchlistId") ?: return
        val watchlistResult = runNullableService(
            call = call,
            action = "get watchlist by id",
            operation = { watchlistService.getWatchlistById(watchlistId) },
        )
        if (watchlistResult.handledError) {
            return
        }
        val watchlist = watchlistResult.value

        if (watchlist == null) {
            respondDetail(
                call = call,
                status = HttpStatusCode.NotFound,
                detail = "Watchlist '$watchlistId' not found",
            )
            return
        }
        respondJson(call, HttpStatusCode.OK, watchlist)
    }

    private suspend fun handleGetWatchlistByName(call: ApplicationCall) {
        val name: String = call.parameters["name"]?.trim().orEmpty()
        if (name.isEmpty()) {
            respondDetail(
                call = call,
                status = HttpStatusCode.BadRequest,
                detail = "Path parameter 'name' is required.",
            )
            return
        }

        val watchlistResult = runNullableService(
            call = call,
            action = "get watchlist by name",
            operation = { watchlistService.getWatchlistByName(name) },
        )
        if (watchlistResult.handledError) {
            return
        }
        val watchlist = watchlistResult.value

        if (watchlist == null) {
            respondDetail(
                call = call,
                status = HttpStatusCode.NotFound,
                detail = "Watchlist '$name' not found",
            )
            return
        }
        respondJson(call, HttpStatusCode.OK, watchlist)
    }

    private suspend fun handleUpdateWatchlist(call: ApplicationCall) {
        val watchlistId: Long = parseLongPathParam(call, "watchlistId") ?: return
        val inputData: UpdateWatchlistInput = parseUpdateWatchlistBody(call) ?: return

        val updatedResult = runNullableService(
            call = call,
            action = "update watchlist",
            operation = { watchlistService.updateWatchlist(watchlistId, inputData) },
        )
        if (updatedResult.handledError) {
            return
        }
        val updated = updatedResult.value

        if (updated == null) {
            respondDetail(
                call = call,
                status = HttpStatusCode.NotFound,
                detail = "Watchlist '$watchlistId' not found",
            )
            return
        }
        respondJson(call, HttpStatusCode.OK, updated)
    }

    private suspend fun handleDeleteWatchlist(call: ApplicationCall) {
        val watchlistId: Long = parseLongPathParam(call, "watchlistId") ?: return
        val deleted = runService(
            call = call,
            action = "delete watchlist",
            operation = { watchlistService.deleteWatchlist(watchlistId) },
        ) ?: return

        if (deleted.not()) {
            respondDetail(
                call = call,
                status = HttpStatusCode.NotFound,
                detail = "Watchlist '$watchlistId' not found",
            )
            return
        }
        respondDeleted(call)
    }

    private suspend fun handleCreateWatchlistStock(call: ApplicationCall) {
        val payload: CreateWatchlistStockInput = parseJsonBody(
            call = call,
            errorContext = "create watchlist stock mapping",
        ) ?: return

        val created = runService(
            call = call,
            action = "create watchlist stock mapping",
            operation = { watchlistService.createWatchlistStock(payload) },
        ) ?: return

        respondJson(call, HttpStatusCode.Created, created)
    }

    private suspend fun handleGetWatchlistStock(call: ApplicationCall) {
        val watchlistId: Long = parseLongPathParam(call, "watchlistId") ?: return
        val stockId: Long = parseLongPathParam(call, "stockId") ?: return
        val mappingResult = runNullableService(
            call = call,
            action = "get watchlist stock mapping",
            operation = { watchlistService.getWatchlistStock(watchlistId, stockId) },
        )
        if (mappingResult.handledError) {
            return
        }
        val mapping = mappingResult.value

        if (mapping == null) {
            respondDetail(
                call = call,
                status = HttpStatusCode.NotFound,
                detail = "Mapping '$watchlistId:$stockId' not found",
            )
            return
        }
        respondJson(call, HttpStatusCode.OK, mapping)
    }

    private suspend fun handleListWatchlistItems(call: ApplicationCall) {
        val watchlistId: Long = parseLongPathParam(call, "watchlistId") ?: return
        val mappings = runService(
            call = call,
            action = "list stocks for watchlist",
            operation = { watchlistService.listStocksForWatchlist(watchlistId) },
        ) ?: return

        respondJson(call, HttpStatusCode.OK, mappings)
    }

    private suspend fun handleUpdateWatchlistStock(call: ApplicationCall) {
        val watchlistId: Long = parseLongPathParam(call, "watchlistId") ?: return
        val stockId: Long = parseLongPathParam(call, "stockId") ?: return
        val inputData: UpdateWatchlistStockInput = parseUpdateWatchlistStockBody(call) ?: return

        val updatedResult = runNullableService(
            call = call,
            action = "update watchlist stock mapping",
            operation = { watchlistService.updateWatchlistStock(watchlistId, stockId, inputData) },
        )
        if (updatedResult.handledError) {
            return
        }
        val updated = updatedResult.value

        if (updated == null) {
            respondDetail(
                call = call,
                status = HttpStatusCode.NotFound,
                detail = "Mapping '$watchlistId:$stockId' not found",
            )
            return
        }
        respondJson(call, HttpStatusCode.OK, updated)
    }

    private suspend fun handleDeleteWatchlistStock(call: ApplicationCall) {
        val watchlistId: Long = parseLongPathParam(call, "watchlistId") ?: return
        val stockId: Long = parseLongPathParam(call, "stockId") ?: return
        val deleted = runService(
            call = call,
            action = "delete watchlist stock mapping",
            operation = { watchlistService.deleteWatchlistStock(watchlistId, stockId) },
        ) ?: return

        if (deleted.not()) {
            respondDetail(
                call = call,
                status = HttpStatusCode.NotFound,
                detail = "Mapping '$watchlistId:$stockId' not found",
            )
            return
        }
        respondDeleted(call)
    }

    private suspend fun parseUpdateStockBody(call: ApplicationCall): UpdateStockInput? {
        val bodyObject: JsonObject = parseJsonObjectBody(call, "update stock") ?: return null
        val payload: UpdateStockPayload = decodeBodyPayload(
            call = call,
            bodyObject = bodyObject,
            errorContext = "update stock",
        ) ?: return null

        val fieldsToUpdate: MutableSet<StockUpdateField> = mutableSetOf()
        if (bodyObject.containsKey("company_name")) {
            fieldsToUpdate.add(StockUpdateField.COMPANY_NAME)
        }
        if (bodyObject.containsKey("groww_symbol")) {
            fieldsToUpdate.add(StockUpdateField.GROWW_SYMBOL)
        }
        if (bodyObject.containsKey("kite_symbol")) {
            fieldsToUpdate.add(StockUpdateField.KITE_SYMBOL)
        }
        if (bodyObject.containsKey("description")) {
            fieldsToUpdate.add(StockUpdateField.DESCRIPTION)
        }
        if (bodyObject.containsKey("rating")) {
            fieldsToUpdate.add(StockUpdateField.RATING)
        }
        if (bodyObject.containsKey("tags")) {
            fieldsToUpdate.add(StockUpdateField.TAGS)
        }

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

    private suspend fun parseUpdateWatchlistBody(call: ApplicationCall): UpdateWatchlistInput? {
        val bodyObject: JsonObject = parseJsonObjectBody(call, "update watchlist") ?: return null
        val payload: UpdateWatchlistPayload = decodeBodyPayload(
            call = call,
            bodyObject = bodyObject,
            errorContext = "update watchlist",
        ) ?: return null

        val fieldsToUpdate: MutableSet<WatchlistUpdateField> = mutableSetOf()
        if (bodyObject.containsKey("name")) {
            fieldsToUpdate.add(WatchlistUpdateField.NAME)
        }
        if (bodyObject.containsKey("description")) {
            fieldsToUpdate.add(WatchlistUpdateField.DESCRIPTION)
        }

        return UpdateWatchlistInput(
            fieldsToUpdate = fieldsToUpdate,
            name = payload.name,
            description = payload.description,
        )
    }

    private suspend fun parseUpdateWatchlistStockBody(
        call: ApplicationCall,
    ): UpdateWatchlistStockInput? {
        val bodyObject: JsonObject = parseJsonObjectBody(
            call = call,
            errorContext = "update watchlist stock mapping",
        ) ?: return null
        val payload: UpdateWatchlistStockPayload = decodeBodyPayload(
            call = call,
            bodyObject = bodyObject,
            errorContext = "update watchlist stock mapping",
        ) ?: return null

        val fieldsToUpdate: MutableSet<WatchlistStockUpdateField> = mutableSetOf()
        if (bodyObject.containsKey("notes")) {
            fieldsToUpdate.add(WatchlistStockUpdateField.NOTES)
        }

        return UpdateWatchlistStockInput(
            fieldsToUpdate = fieldsToUpdate,
            notes = payload.notes,
        )
    }

    private suspend inline fun <reified PayloadT> parseJsonBody(
        call: ApplicationCall,
        errorContext: String,
    ): PayloadT? {
        val bodyText: String = call.receiveText()
        return runCatching {
            requestJson.decodeFromString<PayloadT>(bodyText)
        }.getOrElse { error ->
            respondDetail(
                call = call,
                status = HttpStatusCode.BadRequest,
                detail = "Invalid request body for '$errorContext': ${error.message}",
            )
            null
        }
    }

    private suspend fun parseJsonObjectBody(
        call: ApplicationCall,
        errorContext: String,
    ): JsonObject? {
        val bodyText: String = call.receiveText()
        val parsedElement = runCatching {
            requestJson.parseToJsonElement(bodyText)
        }.getOrElse { error ->
            respondDetail(
                call = call,
                status = HttpStatusCode.BadRequest,
                detail = "Invalid request body for '$errorContext': ${error.message}",
            )
            return null
        }

        val bodyObject: JsonObject = parsedElement as? JsonObject ?: run {
            respondDetail(
                call = call,
                status = HttpStatusCode.BadRequest,
                detail = "Request body for '$errorContext' must be a JSON object.",
            )
            return null
        }
        return bodyObject
    }

    private suspend inline fun <reified PayloadT> decodeBodyPayload(
        call: ApplicationCall,
        bodyObject: JsonObject,
        errorContext: String,
    ): PayloadT? {
        return runCatching {
            requestJson.decodeFromJsonElement<PayloadT>(bodyObject)
        }.getOrElse { error ->
            respondDetail(
                call = call,
                status = HttpStatusCode.BadRequest,
                detail = "Invalid request body for '$errorContext': ${error.message}",
            )
            null
        }
    }

    private suspend fun parseLongPathParam(call: ApplicationCall, name: String): Long? {
        val rawValue: String = call.parameters[name]?.trim().orEmpty()
        if (rawValue.isEmpty()) {
            respondDetail(
                call = call,
                status = HttpStatusCode.BadRequest,
                detail = "Path parameter '$name' is required.",
            )
            return null
        }

        val parsed: Long = rawValue.toLongOrNull() ?: run {
            respondDetail(
                call = call,
                status = HttpStatusCode.BadRequest,
                detail = "Path parameter '$name' must be an integer.",
            )
            return null
        }
        return parsed
    }

    private suspend fun parseLimit(call: ApplicationCall): Int? {
        val rawLimit: String = call.request.queryParameters["limit"]?.trim().orEmpty()
        if (rawLimit.isEmpty()) {
            return 200
        }
        val limit: Int = rawLimit.toIntOrNull() ?: run {
            respondDetail(
                call = call,
                status = HttpStatusCode.BadRequest,
                detail = "Query parameter 'limit' must be an integer.",
            )
            return null
        }
        return limit
    }

    private suspend fun <ResultT> runService(
        call: ApplicationCall,
        action: String,
        operation: () -> ResultT,
    ): ResultT? {
        return try {
            operation()
        } catch (error: WatchlistValidationError) {
            respondDetail(
                call = call,
                status = HttpStatusCode.BadRequest,
                detail = error.message ?: "Validation failed.",
            )
            null
        } catch (error: WatchlistServiceNotConfiguredError) {
            respondDetail(
                call = call,
                status = HttpStatusCode.ServiceUnavailable,
                detail = error.message ?: "Watchlist service is not configured.",
            )
            null
        } catch (error: WatchlistServiceError) {
            respondDetail(
                call = call,
                status = HttpStatusCode.InternalServerError,
                detail = "Watchlist API error while '$action': ${error.message}",
            )
            null
        }
    }

    private suspend fun <ResultT> runNullableService(
        call: ApplicationCall,
        action: String,
        operation: () -> ResultT?,
    ): NullableServiceResult<ResultT> {
        return try {
            NullableServiceResult(
                handledError = false,
                value = operation(),
            )
        } catch (error: WatchlistValidationError) {
            respondDetail(
                call = call,
                status = HttpStatusCode.BadRequest,
                detail = error.message ?: "Validation failed.",
            )
            NullableServiceResult(handledError = true, value = null)
        } catch (error: WatchlistServiceNotConfiguredError) {
            respondDetail(
                call = call,
                status = HttpStatusCode.ServiceUnavailable,
                detail = error.message ?: "Watchlist service is not configured.",
            )
            NullableServiceResult(handledError = true, value = null)
        } catch (error: WatchlistServiceError) {
            respondDetail(
                call = call,
                status = HttpStatusCode.InternalServerError,
                detail = "Watchlist API error while '$action': ${error.message}",
            )
            NullableServiceResult(handledError = true, value = null)
        }
    }

    private suspend inline fun <reified PayloadT> respondJson(
        call: ApplicationCall,
        status: HttpStatusCode,
        payload: PayloadT,
    ) {
        call.respondText(
            status = status,
            text = requestJson.encodeToString(payload),
            contentType = ContentType.Application.Json,
        )
    }

    private suspend fun respondDeleted(call: ApplicationCall) {
        val payload = buildJsonObject {
            put("deleted", true)
        }
        call.respondText(
            status = HttpStatusCode.OK,
            text = payload.toString(),
            contentType = ContentType.Application.Json,
        )
    }

    private suspend fun respondDetail(
        call: ApplicationCall,
        status: HttpStatusCode,
        detail: String,
    ) {
        val payload = buildJsonObject {
            put("detail", detail)
        }
        call.respondText(
            status = status,
            text = payload.toString(),
            contentType = ContentType.Application.Json,
        )
    }

    private data class NullableServiceResult<ResultT>(
        val handledError: Boolean,
        val value: ResultT?,
    )
}

fun Route.registerWatchlistResource(
    resource: WatchlistResource,
) {
    resource.register(this)
}
