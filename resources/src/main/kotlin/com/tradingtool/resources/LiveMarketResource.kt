package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.kite.LiveMarketUpdate
import com.tradingtool.core.kite.TickSnapshot
import com.tradingtool.core.kite.TickStore
import com.tradingtool.core.kite.TickerSubscriptions
import com.tradingtool.core.kite.InstrumentCache
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.sse.Sse
import jakarta.ws.rs.sse.SseEventSink
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Path("/api/market")
class LiveMarketResource @Inject constructor(
    private val tickStore: TickStore,
    private val instrumentCache: InstrumentCache,
    private val tickerSubscriptions: TickerSubscriptions
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val activeSessions = ConcurrentHashMap<String, LiveSession>()
    private val sessionCleaner = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "live-market-session-cleaner").also { it.isDaemon = true }
    }.also { executor ->
        executor.scheduleAtFixedRate(::cleanupClosedSessions, 5, 5, TimeUnit.SECONDS)
    }

    @GET
    @Path("/live")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    fun streamLiveMarket(
        @QueryParam("symbols") symbols: String?,
        @QueryParam("buyerDominancePct") _buyerDominancePct: Double?,
        @Context sseEventSink: SseEventSink,
        @Context sse: Sse
    ) {
        val requestedSymbols = symbols
            ?.split(",")
            ?.map { it.trim().uppercase(Locale.ROOT) }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

        if (requestedSymbols.isEmpty()) {
            try { sseEventSink.close() } catch (e: Exception) {}
            return
        }

        if (!tickerSubscriptions.isStreamingAvailable()) {
            try { sseEventSink.close() } catch (e: Exception) {}
            return
        }

        val resolvedSymbols = requestedSymbols.mapNotNull(::resolveSymbol)
        if (resolvedSymbols.isEmpty()) {
            try { sseEventSink.close() } catch (e: Exception) {}
            return
        }

        resolvedSymbols
            .map { resolvedSymbol -> resolvedSymbol.instrumentToken }
            .distinct()
            .forEach { token -> tickerSubscriptions.addInstrument(token) }

        val sessionId = UUID.randomUUID().toString()
        val session = LiveSession(
            id = sessionId,
            sink = sseEventSink,
            sse = sse,
            symbolsByToken = resolvedSymbols.associateBy { resolvedSymbol -> resolvedSymbol.instrumentToken },
            tokens = resolvedSymbols.map { resolvedSymbol -> resolvedSymbol.instrumentToken }.distinct(),
        )

        session.listener = listener@{ tick ->
            val resolvedSymbol = session.symbolsByToken[tick.instrumentToken] ?: return@listener
            if (!sendUpdates(session, listOf(toLiveMarketUpdate(resolvedSymbol, tick)))) {
                cleanupSession(session.id)
            }
        }

        activeSessions[sessionId] = session
        tickStore.addListener(session.listener)

        if (!sendUpdates(session, buildUpdates(session.symbolsByToken.values.toList()))) {
            cleanupSession(session.id)
        }
    }

    private fun resolveSymbol(originalSymbol: String): ResolvedSymbol? {
        val parts = originalSymbol.split(":")
        val exchange = if (parts.size == 2) parts[0] else "NSE"
        val tradingSymbol = if (parts.size == 2) parts[1] else originalSymbol
        val token = instrumentCache.token(exchange, tradingSymbol) ?: return null
        return ResolvedSymbol(
            symbol = originalSymbol,
            instrumentToken = token,
        )
    }

    private fun buildUpdates(symbols: List<ResolvedSymbol>): List<LiveMarketUpdate> {
        return symbols.mapNotNull { resolvedSymbol ->
            val tick = tickStore.get(resolvedSymbol.instrumentToken) ?: return@mapNotNull null
            toLiveMarketUpdate(resolvedSymbol, tick)
        }
    }

    private fun toLiveMarketUpdate(resolvedSymbol: ResolvedSymbol, tick: TickSnapshot): LiveMarketUpdate {
        return LiveMarketUpdate(
            symbol = resolvedSymbol.symbol,
            instrumentToken = resolvedSymbol.instrumentToken,
            ltp = tick.ltp,
            averagePrice = tick.averagePrice,
            changePercent = if (tick.close > 0) ((tick.ltp - tick.close) / tick.close) * 100 else 0.0,
            open = tick.open,
            high = tick.high,
            low = tick.low,
            volume = tick.volume,
            buyQuantity = tick.buyQuantity,
            sellQuantity = tick.sellQuantity,
            buyPressurePct = null,
            sellPressurePct = null,
            buyerDominancePass = null,
            pressureSide = "NEUTRAL",
            avgVol20d = null,
            volumeHeat = null,
        )
    }

    private fun sendUpdates(session: LiveSession, updates: List<LiveMarketUpdate>): Boolean {
        if (updates.isEmpty()) {
            return true
        }

        synchronized(session.sendLock) {
            if (session.sink.isClosed) {
                return false
            }

            val event = session.sse.newEventBuilder()
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(Array<LiveMarketUpdate>::class.java, updates.toTypedArray())
                .build()

            return try {
                session.sink.send(event).toCompletableFuture().get()
                true
            } catch (error: Exception) {
                log.debug("LiveMarket SSE send failed for session {}", session.id, error)
                false
            }
        }
    }

    private fun cleanupClosedSessions() {
        activeSessions.values
            .filter { session -> session.sink.isClosed }
            .forEach { session -> cleanupSession(session.id) }
    }

    private fun cleanupSession(sessionId: String) {
        val session = activeSessions.remove(sessionId) ?: return

        tickStore.removeListener(session.listener)
        session.tokens.forEach { token -> tickerSubscriptions.removeInstrument(token) }
        try {
            session.sink.close()
        } catch (error: Exception) {
            log.debug("Failed to close LiveMarket SSE session {}", sessionId, error)
        }
    }

    private data class ResolvedSymbol(
        val symbol: String,
        val instrumentToken: Long,
    )

    private class LiveSession(
        val id: String,
        val sink: SseEventSink,
        val sse: Sse,
        val symbolsByToken: Map<Long, ResolvedSymbol>,
        val tokens: List<Long>,
    ) {
        val sendLock: Any = Any()
        lateinit var listener: (TickSnapshot) -> Unit
    }
}
