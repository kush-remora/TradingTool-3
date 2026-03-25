package com.tradingtool.resources

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.TickSnapshot
import com.tradingtool.core.kite.TickStore
import com.tradingtool.core.strategy.remora.RemoraService
import com.tradingtool.core.watchlist.IndicatorService
import com.tradingtool.core.watchlist.WatchlistService
import com.tradingtool.resources.common.accepted
import com.tradingtool.resources.common.badRequest
import com.tradingtool.resources.common.endpoint
import com.tradingtool.resources.common.notFound
import com.tradingtool.resources.common.ok
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.sse.Sse
import jakarta.ws.rs.sse.SseEventSink
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class RefreshRequest(val tags: List<String> = emptyList())

@Path("/api/watchlist")
@Produces(MediaType.APPLICATION_JSON)
class WatchlistResource @Inject constructor(
    private val indicatorService: IndicatorService,
    private val watchlistService: WatchlistService,
    private val kiteClient: KiteConnectClient,
    private val resourceScope: ResourceScope,
    private val tickStore: TickStore,
    private val remoraService: RemoraService,
) {
    private val ioScope = resourceScope.ioScope
    private val objectMapper = ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())

    // One scheduler shared across all SSE connections — each connection registers its own sender.
    private val heartbeatSenders = CopyOnWriteArrayList<() -> Unit>()
    private val heartbeatScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "sse-heartbeat").also { it.isDaemon = true }
    }

    init {
        // Send a SSE comment line every 30s to prevent Render's proxy from closing idle connections.
        heartbeatScheduler.scheduleAtFixedRate(
            {
                heartbeatSenders.forEach { sender ->
                    try { sender() } catch (_: Exception) { /* stale sink — tick listener will clean up */ }
                }
            },
            30, 30, TimeUnit.SECONDS
        )
    }

    // ── Watchlist ─────────────────────────────────────────────────────────────

    /**
     * Returns computed indicators (SMA, RSI, MACD, etc.) for all stocks under [tag].
     * Served from Redis L1; falls back to Postgres L2 on cache miss.
     */
    @GET
    @Path("/indicators")
    fun getIndicatorsByTag(@QueryParam("tag") tag: String?): CompletableFuture<Response> = ioScope.endpoint {
        if (tag.isNullOrBlank()) return@endpoint badRequest("Query parameter 'tag' is required")
        ok(indicatorService.getIndicatorsForTag(tag.trim()))
    }

    /**
     * Returns a merged watchlist row per stock under [tag]:
     * static info + computed indicators (Redis L1 / Postgres L2) + live LTP (Kite L0).
     * Live fields are null if Kite is unauthenticated or quotes are unavailable.
     */
    @GET
    @Path("/rows")
    fun getRows(@QueryParam("tag") tag: String?): CompletableFuture<Response> = ioScope.endpoint {
        if (tag.isNullOrBlank()) return@endpoint badRequest("Query parameter 'tag' is required")
        ok(watchlistService.getRows(tag.trim()))
    }

    /**
     * Returns raw OHLCV JSON for a stock (1 year, daily bars) from Redis L1.
     * Returns 404 if cold — the next cron run will repopulate it.
     */
    @GET
    @Path("/ohlcv/{instrumentToken}")
    fun getOhlcv(@PathParam("instrumentToken") instrumentToken: Long): CompletableFuture<Response> = ioScope.endpoint {
        val raw = indicatorService.getHistoricalOhlcv(instrumentToken)
            ?: return@endpoint notFound("OHLCV data not yet available for token $instrumentToken. It will be populated on the next cron run.")
        // Return the pre-serialized JSON string directly — do not wrap it.
        Response.ok(raw, MediaType.APPLICATION_JSON).build()
    }

    /**
     * Triggers a background indicator refresh and returns 202 immediately.
     * Empty/missing [tags] refreshes all stocks. Non-empty [tags] refreshes only those tags.
     */
    @POST
    @Path("/refresh")
    @Consumes(MediaType.APPLICATION_JSON)
    fun refresh(request: RefreshRequest?): CompletableFuture<Response> = ioScope.endpoint {
        val tags = request?.tags?.filter { it.isNotBlank() } ?: emptyList()

        // Fire-and-forget: sibling coroutine so this handler returns 202 immediately.
        ioScope.launch {
            if (tags.isEmpty()) {
                indicatorService.refreshAll(kiteClient)
            } else {
                tags.distinct().forEach { tag -> indicatorService.refreshTag(kiteClient, tag) }
            }
        }

        val message = if (tags.isEmpty()) "Refreshing all stocks" else "Refreshing tags: ${tags.joinToString()}"
        accepted(mapOf("message" to message))
    }

    // ── Live Stream ───────────────────────────────────────────────────────────

    /**
     * GET /api/watchlist/stream — Server-Sent Events stream of live tick snapshots.
     *
     * Flow:
     * 1. On connect: flush current tick snapshot for all subscribed instruments immediately.
     * 2. On each new tick: push a JSON event to the client.
     * 3. Every 30s: send a SSE comment heartbeat to keep Render's proxy alive.
     * 4. On disconnect: self-removing listeners clean up from TickStore and heartbeat list.
     */
    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    fun stream(@Context sink: SseEventSink, @Context sse: Sse) {
        tickStore.getAll().forEach { tick ->
            if (!sink.isClosed) sendTick(sink, sse, tick)
        }

        val listenerRef  = AtomicReference<((TickSnapshot) -> Unit)?>(null)
        val heartbeatRef = AtomicReference<(() -> Unit)?>(null)

        fun cleanup() {
            listenerRef.get()?.let  { tickStore.removeListener(it) }
            heartbeatRef.get()?.let { heartbeatSenders.remove(it) }
        }

        val listener: (TickSnapshot) -> Unit = { tick ->
            if (sink.isClosed) {
                cleanup()
            } else {
                try {
                    sendTick(sink, sse, tick)
                } catch (_: Exception) {
                    cleanup()
                }
            }
        }

        val heartbeatSender: () -> Unit = {
            if (sink.isClosed) {
                cleanup()
            } else {
                sink.send(sse.newEventBuilder().comment("heartbeat").build())
            }
        }

        listenerRef.set(listener)
        heartbeatRef.set(heartbeatSender)
        tickStore.addListener(listener)
        heartbeatSenders.add(heartbeatSender)
    }

    private fun sendTick(sink: SseEventSink, sse: Sse, tick: TickSnapshot) {
        sink.send(
            sse.newEventBuilder()
                .data(objectMapper.writeValueAsString(tick))
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .build()
        )
    }

    // ── Remora Strategy ───────────────────────────────────────────────────────

    /**
     * GET /api/watchlist/remora — Remora accumulation/distribution signals.
     * Optional filter: ?type=ACCUMULATION or ?type=DISTRIBUTION
     */
    @GET
    @Path("/remora")
    fun getRemoraSignals(@QueryParam("type") type: String?): CompletableFuture<Response> = ioScope.endpoint {
        ok(remoraService.getSignals(type?.trim()?.uppercase()?.takeIf { it.isNotBlank() }))
    }
}
