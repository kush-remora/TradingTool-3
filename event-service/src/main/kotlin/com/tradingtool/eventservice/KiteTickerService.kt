package com.tradingtool.eventservice

import com.tradingtool.core.kite.KiteConnectClient
import com.tradingtool.core.kite.TickSnapshot
import com.tradingtool.core.kite.TickStore
import com.tradingtool.core.kite.TickerSubscriptions
import com.zerodhatech.ticker.KiteTicker
import com.zerodhatech.ticker.OnError
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
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
 * - [startWithMarketSchedule] — called at startup. Starts immediately if within market hours,
 *   and schedules daily start/stop at NSE open/close times.
 * - [restart] — called by KiteConnectClient's token refresh callback (token expiry at 6 AM IST).
 * - [addInstrument] / [removeInstrument] — called by StockResource on watchlist changes.
 */
class KiteTickerService(
    private val kiteClient: KiteConnectClient,
    private val tickStore: TickStore,
) : TickerSubscriptions {

    private val log = LoggerFactory.getLogger(KiteTickerService::class.java)
    private val ist = ZoneId.of("Asia/Kolkata")

    @Volatile private var ticker: KiteTicker? = null
    private val subscribedTokens = CopyOnWriteArrayList<Long>()

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "kite-ticker-scheduler").also { it.isDaemon = true }
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Called from Application.kt after auth and DB stock query.
     * Starts immediately if within market hours, then schedules daily open/close.
     */
    fun startWithMarketSchedule(tokens: List<Long>) {
        subscribedTokens.addAll(tokens)

        if (isMarketHours()) {
            connect()
        } else {
            log.info("[KiteTicker] Outside market hours — waiting for 9:14 AM IST to connect")
        }

        // Task 4: schedule daily market open (9:14 AM IST) and close (3:31 PM IST).
        // India has no DST, so 24h fixed periods are exact.
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
    fun restart(newAccessToken: String) {
        log.info("[KiteTicker] Restarting with refreshed access token")
        stop()
        if (isMarketHours()) connect()
    }

    /** Task 2: Called by StockResource when a new stock is added to the watchlist. */
    override fun addInstrument(token: Long) {
        if (subscribedTokens.contains(token)) return
        subscribedTokens.add(token)
        ticker?.subscribe(arrayListOf(token))
        ticker?.setMode(arrayListOf(token), KiteTicker.modeQuote)
        log.info("[KiteTicker] Subscribed to instrument $token")
    }

    /** Task 2: Called by StockResource when a stock is removed from the watchlist. */
    override fun removeInstrument(token: Long) {
        subscribedTokens.remove(token)
        ticker?.unsubscribe(arrayListOf(token))
        log.info("[KiteTicker] Unsubscribed from instrument $token")
    }

    // ── Private ─────────────────────────────────────────────────────────────

    private fun onMarketOpen() {
        log.info("[KiteTicker] Market open — connecting")
        if (kiteClient.isAuthenticated) connect() else log.warn("[KiteTicker] Market open but Kite not authenticated — skipping connect")
    }

    private fun onMarketClose() {
        log.info("[KiteTicker] Market close — disconnecting")
        stop()
    }

    private fun connect() {
        val accessToken = kiteClient.accessToken
        val apiKey = kiteClient.apiKey
        val tokens = subscribedTokens.toList()

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
            t.setMode(tokenList, KiteTicker.modeQuote)  // LTP + volume + OHLC (44 bytes/tick)
            log.info("[KiteTicker] Connected — subscribed to ${tokens.size} instruments")
        }

        t.setOnTickerArrivalListener { ticks ->
            ticks.forEach { tick ->
                tickStore.put(
                    TickSnapshot(
                        instrumentToken = tick.instrumentToken,
                        ltp             = tick.lastTradedPrice,
                        volume          = tick.volumeTradedToday,
                        changePercent   = tick.change,
                        open            = tick.openPrice,
                        high            = tick.highPrice,
                        low             = tick.lowPrice,
                        close           = tick.closePrice,
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

    private fun isMarketHours(): Boolean {
        val now = ZonedDateTime.now(ist)
        val open  = now.withHour(9).withMinute(14).withSecond(0).withNano(0)
        val close = now.withHour(15).withMinute(31).withSecond(0).withNano(0)
        return now.isAfter(open) && now.isBefore(close)
    }

    /** Milliseconds until the next occurrence of [hour]:[minute] IST. */
    private fun millisUntil(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now(ist)
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
