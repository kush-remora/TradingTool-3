package com.tradingtool.core.kite

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory store for the latest tick snapshot per instrument.
 *
 * KiteTickerService writes ticks via [put] on each WebSocket message.
 * LiveStreamResource collects [ticks] (a SharedFlow) to push updates to SSE clients.
 *
 * Thread-safety:
 * - [store] is a ConcurrentHashMap — safe for concurrent read/write.
 * - [_ticks] uses tryEmit() which is non-blocking and non-suspending, safe to call
 *   from the Kite WebSocket callback thread.
 *
 * Overflow strategy: DROP_OLDEST — the newest tick is always more valuable than
 * a queued old one. In practice the 64-item buffer is never exhausted with typical
 * NSE watchlists (≤100 symbols at ~1 tick/s per symbol during market hours).
 */
class TickStore {

    private val store = ConcurrentHashMap<Long, TickSnapshot>()
    private val listeners = CopyOnWriteArrayList<(TickSnapshot) -> Unit>()

    private val _ticks = MutableSharedFlow<TickSnapshot>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Hot flow of incoming ticks. Each collector receives every future tick independently. */
    val ticks: SharedFlow<TickSnapshot> = _ticks.asSharedFlow()

    /** Called by KiteTickerService on each incoming WebSocket tick. */
    fun put(tick: TickSnapshot) {
        store[tick.instrumentToken] = tick
        _ticks.tryEmit(tick)
        listeners.forEach { it(tick) }
    }

    fun addListener(listener: (TickSnapshot) -> Unit) { listeners.add(listener) }
    fun removeListener(listener: (TickSnapshot) -> Unit) { listeners.remove(listener) }

    /** Returns the latest snapshot for all subscribed instruments. Used for initial SSE flush. */
    fun getAll(): List<TickSnapshot> = store.values.toList()

    fun get(instrumentToken: Long): TickSnapshot? = store[instrumentToken]
}
