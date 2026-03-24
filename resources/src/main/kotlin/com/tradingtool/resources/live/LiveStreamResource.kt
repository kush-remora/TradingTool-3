package com.tradingtool.resources.live

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import com.tradingtool.core.kite.TickSnapshot
import com.tradingtool.core.kite.TickStore
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.sse.Sse
import jakarta.ws.rs.sse.SseEventSink
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Streams live tick snapshots to the browser via Server-Sent Events.
 *
 * Flow:
 * 1. On connect: flush current tick snapshot for all subscribed instruments immediately.
 * 2. On each new tick: push a JSON event to the client.
 * 3. Every 30s: send a SSE comment heartbeat to keep Render's proxy from closing the connection.
 * 4. On disconnect: self-removing listeners clean up from both TickStore and heartbeat list.
 */
@Path("/api/live")
class LiveStreamResource @Inject constructor(
    private val tickStore: TickStore,
) {
    private val objectMapper = ObjectMapper()

    // Task 3: one scheduler shared across all connections. Each connection registers its own sender.
    private val heartbeatSenders = CopyOnWriteArrayList<() -> Unit>()
    private val heartbeatScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "sse-heartbeat").also { it.isDaemon = true }
    }

    init {
        // Send a SSE comment line every 30s. Browsers ignore comment lines;
        // Render's proxy resets its idle-connection timer on any data received.
        heartbeatScheduler.scheduleAtFixedRate(
            {
                heartbeatSenders.forEach { sender ->
                    try { sender() } catch (_: Exception) { /* stale sink — tick listener will clean up */ }
                }
            },
            30, 30, TimeUnit.SECONDS
        )
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    fun stream(@Context sink: SseEventSink, @Context sse: Sse) {
        // Flush current snapshot immediately — no waiting for next tick.
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
}
