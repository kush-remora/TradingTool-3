package com.tradingtool.resources

import com.google.inject.Inject
import com.tradingtool.core.kite.LiveMarketUpdate
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

@Path("/api/market")
class LiveMarketResource @Inject constructor(
    private val tickStore: TickStore,
    private val instrumentCache: InstrumentCache,
    private val tickerSubscriptions: TickerSubscriptions
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GET
    @Path("/live")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    fun streamLiveMarket(
        @QueryParam("symbols") symbols: String?,
        @QueryParam("buyerDominancePct") buyerDominancePct: Double?,
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

        requestedSymbols.forEach { originalSymbol ->
            val parts = originalSymbol.split(":")
            val exchange = if (parts.size == 2) parts[0] else "NSE"
            val tradingSymbol = if (parts.size == 2) parts[1] else originalSymbol
            val token = instrumentCache.token(exchange, tradingSymbol)
            if (token != null) {
                tickerSubscriptions.addInstrument(token)
            }
        }

        val thread = Thread {
            try {
                while (!sseEventSink.isClosed) {
                    val updates = requestedSymbols.mapNotNull { originalSymbol ->
                        val parts = originalSymbol.split(":")
                        val exchange = if (parts.size == 2) parts[0] else "NSE"
                        val tradingSymbol = if (parts.size == 2) parts[1] else originalSymbol
                        val token = instrumentCache.token(exchange, tradingSymbol) ?: return@mapNotNull null
                        val tick = tickStore.get(token) ?: return@mapNotNull null
                        
                        LiveMarketUpdate(
                            symbol = originalSymbol,
                            instrumentToken = token,
                            ltp = tick.ltp,
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
                            volumeHeat = null
                        )
                    }

                    for (update in updates) {
                        if (sseEventSink.isClosed) break
                        val event = sse.newEventBuilder()
                            .mediaType(MediaType.APPLICATION_JSON_TYPE)
                            .data(LiveMarketUpdate::class.java, update)
                            .build()
                        sseEventSink.send(event)
                    }

                    Thread.sleep(1000)
                }
            } catch (e: Exception) {
                log.error("LiveMarket SSE error", e)
            } finally {
                try { sseEventSink.close() } catch (e: Exception) {}
            }
        }
        thread.isDaemon = true
        thread.start()
    }
}
