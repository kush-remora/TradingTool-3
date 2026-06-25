package com.tradingtool.eventservice

import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.NseMarketClock
import com.tradingtool.core.kite.TickSnapshot
import com.tradingtool.core.kite.TickStore
import com.tradingtool.core.kite.TickerSubscriptions
import com.zerodhatech.ticker.KiteTicker
import com.zerodhatech.ticker.OnError
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Connects to Kite's WebSocket ticker and streams live ticks into [TickStore].
 *
 * Implements [TickerSubscriptions] so resources can dynamically add/remove instruments
 * without depending on the event-service module directly.
 *
 * Lifecycle:
 * - [start] — called at startup. Registers daily open/close handling.
 * - [restart] — called by KiteConnectClient's token refresh callback (token expiry at 6 AM IST).
 * - [addInstrument] / [removeInstrument] — called by HTTP resources as pages open/close.
 */
class KiteTickerService(
    private val kiteClient: KiteConnectClient,
    private val tickStore: TickStore,
) : TickerSubscriptions {

    private val log = LoggerFactory.getLogger(KiteTickerService::class.java)

    @Volatile private var ticker: KiteTicker? = null
    @Volatile private var schedulerStarted: Boolean = false
    private val subscriptionLock = Any()
    private val subscriptionCounts = ConcurrentHashMap<Long, Int>()

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "kite-ticker-scheduler").also { it.isDaemon = true }
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Called from Application.kt during startup.
     * Registers daily open/close handling once. The actual ticker connection is lazy
     * and only happens after at least one page subscribes to symbols during market hours.
     */
    fun start() {
        synchronized(subscriptionLock) {
            if (schedulerStarted) {
                return
            }
            schedulerStarted = true
        }

        scheduler.scheduleAtFixedRate(
            ::onMarketOpen,
            millisUntil(9, 14), 24 * 60 * 60 * 1000L, TimeUnit.MILLISECONDS
        )
        scheduler.scheduleAtFixedRate(
            ::onMarketClose,
            millisUntil(15, 31), 24 * 60 * 60 * 1000L, TimeUnit.MILLISECONDS
        )
    }

    fun stop() {
        ticker?.disconnect()
        ticker = null
        log.info("[KiteTicker] Stopped")
    }

    /** Task 1: Called by KiteConnectClient.setTokenRefreshCallback when cron-job refreshes token. */
    fun restart(_newAccessToken: String) {
        log.info("[KiteTicker] Restarting with refreshed access token")
        stop()
        if (isStreamingAvailable() && hasActiveSubscriptions()) {
            connect()
        }
    }

    override fun addInstrument(token: Long) {
        val shouldSubscribeNow = synchronized(subscriptionLock) {
            val count = subscriptionCounts[token] ?: 0
            subscriptionCounts[token] = count + 1
            count == 0
        }

        if (!shouldSubscribeNow) {
            return
        }

        if (ticker == null) {
            if (isStreamingAvailable()) {
                connect()
            }
            return
        }

        ticker?.subscribe(arrayListOf(token))
        ticker?.setMode(arrayListOf(token), KiteTicker.modeFull)
        log.info("[KiteTicker] Subscribed to instrument {}", token)
    }

    override fun removeInstrument(token: Long) {
        val shouldUnsubscribeNow = synchronized(subscriptionLock) {
            val count = subscriptionCounts[token] ?: return@synchronized null
            if (count <= 1) {
                subscriptionCounts.remove(token)
                true
            } else {
                subscriptionCounts[token] = count - 1
                false
            }
        }

        if (shouldUnsubscribeNow != true) {
            return
        }

        ticker?.unsubscribe(arrayListOf(token))
        log.info("[KiteTicker] Unsubscribed from instrument {}", token)

        if (!hasActiveSubscriptions()) {
            stop()
        }
    }

    override fun isStreamingAvailable(): Boolean = kiteClient.isAuthenticated && NseMarketClock.isMarketOpen()

    // ── Private ─────────────────────────────────────────────────────────────

    private fun onMarketOpen() {
        log.info("[KiteTicker] Market open — connecting")
        if (!isStreamingAvailable()) {
            log.warn("[KiteTicker] Market open but Kite not authenticated — skipping connect")
            return
        }
        if (!hasActiveSubscriptions()) {
            log.info("[KiteTicker] Market open but no active page subscriptions — staying idle")
            return
        }
        connect()
    }

    private fun onMarketClose() {
        log.info("[KiteTicker] Market close — disconnecting")
        stop()
    }

    private fun connect() {
        if (!isStreamingAvailable()) {
            return
        }
        if (ticker != null) {
            return
        }

        val accessToken = kiteClient.accessToken
        val apiKey = kiteClient.apiKey
        val tokens = synchronized(subscriptionLock) {
            subscriptionCounts.keys.toList()
        }

        if (tokens.isEmpty()) {
            log.warn("[KiteTicker] connect() called with no subscribed tokens — skipping")
            return
        }

        val t = KiteTicker(accessToken, apiKey)
        t.setTryReconnection(true)
        t.setMaximumRetries(10)
        t.setMaximumRetryInterval(30)

        t.setOnConnectedListener {
            val tokenList = ArrayList(tokens)
            t.subscribe(tokenList)
            t.setMode(tokenList, KiteTicker.modeFull)  // Includes depth + buy/sell quantities.
            log.info("[KiteTicker] Connected — subscribed to ${tokens.size} instruments")
        }

        t.setOnTickerArrivalListener { ticks ->
            ticks.forEach { tick ->
                tickStore.put(
                    TickSnapshot(
                        instrumentToken = tick.instrumentToken,
                        ltp             = tick.lastTradedPrice,
                        averagePrice    = tick.averageTradePrice.takeIf { averagePrice -> averagePrice > 0.0 },
                        volume          = tick.volumeTradedToday,
                        changePercent   = tick.change,
                        open            = tick.openPrice,
                        high            = tick.highPrice,
                        low             = tick.lowPrice,
                        close           = tick.closePrice,
                        buyQuantity     = tick.totalBuyQuantity.toLong(),
                        sellQuantity    = tick.totalSellQuantity.toLong(),
                    )
                )
            }
        }

        t.setOnDisconnectedListener {
            log.warn("[KiteTicker] Disconnected — SDK will retry (tryReconnection=true)")
        }

        t.setOnErrorListener(object : OnError {
            override fun onError(ex: Exception) { log.error("[KiteTicker] Error", ex) }
            override fun onError(ex: com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException) { log.error("[KiteTicker] KiteException: ${ex.message}") }
            override fun onError(error: String)  { log.error("[KiteTicker] Error: $error") }
        })

        t.connect()
        ticker = t
    }

    /** Milliseconds until the next occurrence of [hour]:[minute] IST. */
    private fun millisUntil(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now(NseMarketClock.zone)
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    private fun hasActiveSubscriptions(): Boolean = subscriptionCounts.isNotEmpty()
}
