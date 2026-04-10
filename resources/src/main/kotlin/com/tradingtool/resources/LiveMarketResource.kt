package com.tradingtool.resources

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.inject.Inject
import com.tradingtool.core.di.ResourceScope
import com.tradingtool.core.kite.InstrumentCache
import com.tradingtool.core.kite.LiveMarketUpdate
import com.tradingtool.core.kite.TickSnapshot
import com.tradingtool.core.kite.TickStore
import com.tradingtool.core.watchlist.IndicatorService
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.sse.Sse
import jakarta.ws.rs.sse.SseEventSink
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * SSE endpoint for real-time market updates including context indicators like Volume Heat.
 */
@Path("/api/market")
class LiveMarketResource @Inject constructor(
    private val tickStore: TickStore,
    private val instrumentCache: InstrumentCache,
    private val indicatorService: IndicatorService,
    private val resourceScope: ResourceScope,
) {
    private val log = LoggerFactory.getLogger(LiveMarketResource::class.java)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    
    // One scheduler shared across all SSE connections — each connection registers its own sender.
    private val heartbeatSenders = CopyOnWriteArrayList<() -> Unit>()
    private val heartbeatScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "market-sse-heartbeat").also { it.isDaemon = true }
    }

    init {
        // Send a SSE comment line every 30s to prevent proxies from closing idle connections.
        heartbeatScheduler.scheduleAtFixedRate(
            {
                heartbeatSenders.forEach { sender ->
                    try { sender() } catch (_: Exception) { /* stale sink — listener will clean up */ }
                }
            },
            30, 30, TimeUnit.SECONDS
        )
    }

    /**
     * GET /api/market/live?symbols=NSE:INFY,NSE:RELIANCE
     *
     * Streams live LTP, High, Low, Volume and Volume Heat (Current Vol / 20d Avg Vol).
     */
    @GET
    @Path("/live")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    fun stream(
        @Context sink: SseEventSink,
        @Context sse: Sse,
        @QueryParam("symbols") symbolsStr: String?
    ) {
        val symbols = symbolsStr?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        if (symbols.isEmpty()) {
            sink.close()
            return
        }

        // 1. Resolve symbols to tokens and fetch avg volume context
        val symbolToToken = symbols.mapNotNull { s ->
            val parts = s.split(":")
            if (parts.size != 2) return@mapNotNull null
            val token = instrumentCache.token(parts[0], parts[1]) ?: return@mapNotNull null
            s to token
        }.toMap()

        val tokens = symbolToToken.values.toList()
        
        // Fetch indicators for these tokens (blocking call is fine here as it's at connection start)
        val tokenToAvgVol = runBlocking {
            indicatorService.loadIndicatorsForTokens(tokens)
        }.associate { it.instrumentToken to it.avgVol20d }

        // 2. Push initial snapshots immediately if available
        tokens.forEach { token ->
            tickStore.get(token)?.let { tick ->
                val symbol = symbolToToken.entries.find { it.value == token }?.key ?: return@let
                sendUpdate(sink, sse, symbol, tick, tokenToAvgVol[token])
            }
        }

        // 3. Register listener for future ticks
        val listenerRef = AtomicReference<((TickSnapshot) -> Unit)?>(null)
        val heartbeatRef = AtomicReference<(() -> Unit)?>(null)

        fun cleanup() {
            listenerRef.get()?.let { tickStore.removeListener(it) }
            heartbeatRef.get()?.let { heartbeatSenders.remove(it) }
        }

        val listener: (TickSnapshot) -> Unit = { tick ->
            if (sink.isClosed) {
                cleanup()
            } else if (symbolToToken.values.contains(tick.instrumentToken)) {
                val symbol = symbolToToken.entries.find { it.value == tick.instrumentToken }?.key
                if (symbol != null) {
                    try {
                        sendUpdate(sink, sse, symbol, tick, tokenToAvgVol[tick.instrumentToken])
                    } catch (e: Exception) {
                        log.debug("Failed to send tick for $symbol: ${e.message}")
                        cleanup()
                    }
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

    private fun sendUpdate(
        sink: SseEventSink,
        sse: Sse,
        symbol: String,
        tick: TickSnapshot,
        avgVol20d: Double?
    ) {
        val totalPressure = tick.buyQuantity + tick.sellQuantity
        val buyPressurePct = if (totalPressure > 0L) tick.buyQuantity * 100.0 / totalPressure else null
        val sellPressurePct = if (totalPressure > 0L) tick.sellQuantity * 100.0 / totalPressure else null
        val pressureSide = when {
            buyPressurePct == null || sellPressurePct == null -> "NEUTRAL"
            kotlin.math.abs(buyPressurePct - sellPressurePct) < 4.0 -> "NEUTRAL"
            buyPressurePct > sellPressurePct -> "BUYERS_AGGRESSIVE"
            else -> "SELLERS_AGGRESSIVE"
        }

        val volumeHeat = if (avgVol20d != null && avgVol20d > 0) {
            tick.volume.toDouble() / avgVol20d
        } else null

        val update = LiveMarketUpdate(
            symbol = symbol,
            instrumentToken = tick.instrumentToken,
            ltp = tick.ltp,
            changePercent = tick.changePercent,
            high = tick.high,
            low = tick.low,
            volume = tick.volume,
            buyQuantity = tick.buyQuantity,
            sellQuantity = tick.sellQuantity,
            buyPressurePct = buyPressurePct,
            sellPressurePct = sellPressurePct,
            pressureSide = pressureSide,
            avgVol20d = avgVol20d,
            volumeHeat = volumeHeat
        )

        sink.send(
            sse.newEventBuilder()
                .data(objectMapper.writeValueAsString(update))
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .build()
        )
    }
}
