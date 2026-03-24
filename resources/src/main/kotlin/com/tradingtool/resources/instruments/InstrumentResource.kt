package com.tradingtool.resources.instruments

import com.google.inject.Inject
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.model.stock.InstrumentSearchResult
import com.tradingtool.core.di.ResourceScope
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

@Path("/api/instruments")
@Produces(MediaType.APPLICATION_JSON)
class InstrumentResource @Inject constructor(
    private val instrumentCache: InstrumentCache,
    private val kiteClient: KiteConnectClient,
    private val resourceScope: ResourceScope,
) {
    private val ioScope = resourceScope.ioScope
    private val mutex = Mutex()
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Get all cached instruments (NSE EQ only, ~8k stocks).
     * Returns the full list for client-side search/filtering.
     * Cache is auto-populated if empty (login required first).
     */
    @GET
    @Path("/all")
    fun getAllInstruments(): CompletableFuture<Response> = ioScope.async {
        if (instrumentCache.isEmpty()) {
            mutex.withLock {
                // Double-check if still empty after acquiring lock
                if (instrumentCache.isEmpty()) {
                        try {
                            val instruments = withTimeout(30.seconds) {
                                kiteClient.client().getInstruments("NSE")
                            }
                            instrumentCache.refresh(instruments)
                            log.info("Auto-refreshed {} NSE instruments", instrumentCache.size())
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            log.error("Timeout fetching instruments from Kite", e)
                            return@async Response.status(503)
                                .entity(mapOf("detail" to "Kite fetch timed out (30s)"))
                                .build()
                        } catch (e: IOException) {
                            log.error("Network error fetching instruments from Kite", e)
                            return@async Response.status(503)
                                .entity(mapOf("detail" to "Network error: ${e.message}"))
                                .build()
                        } catch (e: RuntimeException) {
                            log.error("Kite API error fetching instruments", e)
                            return@async Response.status(503)
                                .entity(mapOf("detail" to "Kite API error: ${e.message}"))
                                .build()
                        }
                }
            }
        }

        if (instrumentCache.isEmpty()) {
            return@async Response.status(503)
                .entity(mapOf("detail" to "Instrument cache is empty and could not be populated"))
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
