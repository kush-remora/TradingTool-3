package com.tradingtool.resources.instruments

import com.google.inject.Inject
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.model.watchlist.InstrumentSearchResult
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture

@Path("/api/instruments")
@Produces(MediaType.APPLICATION_JSON)
class InstrumentResource @Inject constructor(
    private val instrumentCache: InstrumentCache,
) {
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Get all cached instruments (NSE EQ only, ~8k stocks).
     * Returns the full list for client-side search/filtering.
     * Cache must be populated (login required first).
     */
    @GET
    @Path("/all")
    fun getAllInstruments(): CompletableFuture<Response> = ioScope.async {
        if (instrumentCache.isEmpty()) {
            return@async Response.status(503)
                .entity(mapOf("detail" to "Instrument cache is empty — Kite login required"))
                .build()
        }

        val results = instrumentCache.all()
            .map { inst ->
                InstrumentSearchResult(
                    instrumentToken = inst.instrument_token,
                    tradingSymbol = inst.tradingsymbol,
                    companyName = inst.name ?: "",
                    exchange = inst.exchange,
                    instrumentType = inst.instrument_type,
                )
            }
            .toList()

        Response.ok(results).build()
    }.asCompletableFuture()

}
