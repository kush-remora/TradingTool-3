# Kite Connect API — TradingTool-3 Reference

Status: Reference document
SDK: `com.zerodhatech.kiteconnect:kiteconnect:3.5.1`
API version: v3
Official docs: https://kite.trade/docs/connect/v3/

---

## 1. What Kite Connect Provides

| Capability | Use in this project |
|---|---|
| Authentication (OAuth2-like flow) | One-time login to get `access_token` |
| Order placement / management | Place, modify, cancel equity/F&O orders |
| Portfolio — holdings + positions | Read current portfolio state |
| Market quotes (LTP, OHLC, full depth) | Live price snapshots for watchlist |
| Instruments list | Master lookup table: symbol → `instrument_token` |
| Historical OHLCV candles | **Primary data source for backtesting** |
| WebSocket ticker (real-time streaming) | Live price feed for `event-service` |
| GTT (Good Till Triggered) orders | Conditional/stop orders without polling |
| Mutual funds | Out of scope for initial migration |

---

## 2. Maven Dependency

Add to **`core/pom.xml`** (shared by all modules):

```xml
<dependency>
    <groupId>com.zerodhatech.kiteconnect</groupId>
    <artifactId>kiteconnect</artifactId>
    <version>3.5.1</version>
</dependency>
```

**No transitive conflicts expected** — the SDK pulls in OkHttp 3.x and org.json. Both are safe alongside Dropwizard's Jackson/Jersey stack.

---

## 3. Authentication Flow

Kite Connect uses a 3-step flow. Access tokens are **valid until 6 AM IST the next day**.

```
Step 1: Your service generates a login URL
        → User opens it in browser and logs in on Zerodha
        → Zerodha redirects to your redirect_url?request_token=XYZ

Step 2: Your service exchanges request_token for access_token (one HTTP POST)

Step 3: Use access_token for all subsequent API calls until it expires
```

### Key classes

```kotlin
import com.zerodhatech.kiteconnect.KiteConnect
import com.zerodhatech.models.User

val kite = KiteConnect("your_api_key")
kite.userId = "your_user_id"

// Step 1 — get login URL, redirect user here
val loginUrl: String = kite.loginURL

// Step 2 — after redirect, exchange request_token
val session: User = kite.generateSession("request_token_from_redirect", "your_api_secret")
kite.setAccessToken(session.accessToken)
kite.setPublicToken(session.publicToken)

// All subsequent calls use the token automatically
```

### Environment variables (add to existing set)

```
KITE_API_KEY=
KITE_API_SECRET=
KITE_USER_ID=
KITE_REDIRECT_URL=
```

Do NOT store `access_token` in env — it's short-lived. Store it in memory or a fast cache (e.g. a singleton bean) and refresh via re-login flow when it expires.

### Session expiry hook

Register this at startup so that 403 responses trigger automatic re-login:

```kotlin
kite.setSessionExpiryHook {
    // re-run login flow here, then kite.setAccessToken(newToken)
}
```

### Token management — recommended pattern for this project

Since this is a solo tool without a browser-based re-login loop, the simplest approach:
1. On startup, check if a valid token is cached (file or in-memory).
2. If not, print the login URL to stdout and wait for a CLI-provided `request_token`.
3. Exchange and cache the resulting `access_token`.
4. Register the expiry hook to repeat step 2-3 on 403.

---

## 4. Instruments List

**Fetch once daily at ~8:30 AM IST and cache in memory.** This is the lookup table for everything else.

```kotlin
// All exchanges (~500k rows gzipped CSV, ~10 MB unzipped)
val all: List<Instrument> = kite.getInstruments()

// Single exchange (recommended — smaller, faster)
val nse: List<Instrument> = kite.getInstruments("NSE")
val nfo: List<Instrument> = kite.getInstruments("NFO")  // F&O
```

### Instrument fields

| Field | Type | Notes |
|---|---|---|
| `instrumentToken` | Long | ID used in historical data + WebSocket subscriptions |
| `exchangeToken` | Long | Exchange-level token |
| `tradingsymbol` | String | e.g. `"RELIANCE"`, `"NIFTY24JANFUT"` |
| `name` | String | Company name |
| `lastPrice` | Double | Last traded price at time of CSV generation |
| `expiry` | Date | null for equities, expiry date for F&O |
| `strike` | Double | Strike price for options, 0 for others |
| `tickSize` | Double | Minimum price movement (e.g. 0.05) |
| `lotSize` | Int | Contract lot size |
| `instrumentType` | String | `"EQ"`, `"FUT"`, `"CE"`, `"PE"` |
| `segment` | String | `"NSE"`, `"NFO-FUT"`, `"NFO-OPT"`, etc. |
| `exchange` | String | `"NSE"`, `"NFO"`, `"BSE"`, `"MCX"`, etc. |

### Suggested caching strategy

```kotlin
// In core — a simple in-memory cache refreshed by cron-job
class InstrumentCache {
    private var cache: Map<String, Instrument> = emptyMap()  // key: "EXCHANGE:SYMBOL"

    fun refresh(kite: KiteConnect) {
        val instruments = kite.getInstruments("NSE") + kite.getInstruments("NFO")
        cache = instruments.associateBy { "${it.exchange}:${it.tradingsymbol}" }
    }

    fun token(exchange: String, symbol: String): Long? =
        cache["$exchange:$symbol"]?.instrumentToken
}
```

---

## 5. Historical Data (Backtesting)

This is the most important API for the backtesting stack.

```kotlin
import com.zerodhatech.models.HistoricalData
import java.text.SimpleDateFormat

val fmt = SimpleDateFormat("yyyy-MM-dd")
val from = fmt.parse("2024-01-01")
val to   = fmt.parse("2024-03-01")

val data: HistoricalData = kite.getHistoricalData(
    from,
    to,
    "408065",   // instrument_token as String
    "day",      // interval — see table below
    false,      // continuous: stitch expired contracts (true for futures backtests)
    false       // oi: include open interest
)

// Candle fields: timeStamp (Date), open, high, low, close, volume, oi (all Double)
data.dataArrayList.forEach { candle ->
    println("${candle.timeStamp} O=${candle.open} H=${candle.high} L=${candle.low} C=${candle.close} V=${candle.volume}")
}
```

### Valid intervals and their max date range per request

| Interval | Max range per call |
|---|---|
| `minute` | 60 days |
| `3minute` | 100 days |
| `5minute` | 100 days |
| `10minute` | 100 days |
| `15minute` | 200 days |
| `30minute` | 200 days |
| `60minute` | 400 days |
| `day` | 2000 days (~5.5 years) |

### Rate limit: 3 requests/second

For large backtests over long date ranges, chunk requests and add throttling:

```kotlin
// Example: fetch 5 years of daily data (needs only 1 request — within 2000-day limit)
// Example: fetch 1 year of 5-minute data → need ceil(365/100) = 4 requests → throttle at 3 req/s

fun chunkedHistorical(
    kite: KiteConnect,
    token: String,
    from: Date,
    to: Date,
    interval: String,
    maxDaysPerChunk: Int
): List<HistoricalData.Candle> {
    val chunks = mutableListOf<HistoricalData.Candle>()
    var chunkStart = from
    while (chunkStart.before(to)) {
        val chunkEnd = minOf(chunkStart.plusDays(maxDaysPerChunk), to)
        chunks += kite.getHistoricalData(chunkStart, chunkEnd, token, interval, false, false).dataArrayList
        chunkStart = chunkEnd
        Thread.sleep(350)  // stay under 3 req/s
    }
    return chunks
}
```

### Integration with ta4j (backtesting)

Kite provides raw OHLCV candles. ta4j consumes them as `Bar` objects:

```kotlin
// Convert Kite candles → ta4j BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.num.DecimalNum
import java.time.ZoneId

fun candlesToBarSeries(candles: List<HistoricalData.Candle>, name: String): BarSeries {
    val series = BaseBarSeriesBuilder().withName(name).withNumTypeOf(DecimalNum::class.java).build()
    candles.forEach { c ->
        series.addBar(
            c.timeStamp.toInstant().atZone(ZoneId.of("Asia/Kolkata")),
            c.open, c.high, c.low, c.close, c.volume
        )
    }
    return series
}
```

---

## 6. Market Quotes

### Quote types

| Method | Instruments limit | Data returned |
|---|---|---|
| `getQuote(instruments)` | 500 | Full snapshot: LTP, OHLC, volume, 5-level depth |
| `getOHLC(instruments)` | 1000 | OHLC + LTP |
| `getLTP(instruments)` | 1000 | LTP only |

### Usage

```kotlin
// Instrument key format: "EXCHANGE:TRADINGSYMBOL"
val keys = arrayOf("NSE:INFY", "NSE:RELIANCE", "BSE:TCS")

val quotes: Map<String, Quote> = kite.getQuote(keys)
quotes["NSE:INFY"]?.let { q ->
    println("LTP: ${q.lastPrice}, Volume: ${q.volume}")
    println("Depth bid[0]: ${q.depth?.buy?.get(0)?.price}")
}

val ltpMap: Map<String, LTPQuote> = kite.getLTP(keys)
```

**Rate limit: 1 request/second for `/quote`.**

---

## 7. Orders

### Constants (always use these, never raw strings)

```kotlin
import com.zerodhatech.kiteconnect.utils.Constants

// Exchange
Constants.EXCHANGE_NSE   // "NSE"
Constants.EXCHANGE_NFO   // "NFO" (F&O)
Constants.EXCHANGE_BSE   // "BSE"
Constants.EXCHANGE_MCX   // "MCX"

// Product
Constants.PRODUCT_CNC    // "CNC"  — delivery (equity)
Constants.PRODUCT_MIS    // "MIS"  — intraday
Constants.PRODUCT_NRML   // "NRML" — F&O overnight

// Order type
Constants.ORDER_TYPE_MARKET  // "MARKET"
Constants.ORDER_TYPE_LIMIT   // "LIMIT"
Constants.ORDER_TYPE_SL      // "SL"   — stop-loss limit
Constants.ORDER_TYPE_SLM     // "SL-M" — stop-loss market

// Transaction type
Constants.TRANSACTION_TYPE_BUY
Constants.TRANSACTION_TYPE_SELL

// Variety
Constants.VARIETY_REGULAR    // "regular"
Constants.VARIETY_AMO        // "amo" — after-market order

// Validity
Constants.VALIDITY_DAY       // "DAY"
Constants.VALIDITY_IOC       // "IOC" — immediate or cancel
```

### Place an order

```kotlin
import com.zerodhatech.models.OrderParams

val params = OrderParams().apply {
    tradingsymbol   = "RELIANCE"
    exchange        = Constants.EXCHANGE_NSE
    transactionType = Constants.TRANSACTION_TYPE_BUY
    orderType       = Constants.ORDER_TYPE_LIMIT
    quantity        = 1
    price           = 2800.0
    product         = Constants.PRODUCT_CNC
    validity        = Constants.VALIDITY_DAY
}

val order = kite.placeOrder(params, Constants.VARIETY_REGULAR)
println("Order ID: ${order.orderId}")
```

### Order management

```kotlin
kite.getOrders()                            // List<Order> — all orders today
kite.getOrderHistory("orderId")             // List<Order> — order audit trail
kite.getTrades()                            // List<Trade> — all fills today
kite.getOrderTrades("orderId")              // List<Trade> — fills for one order
kite.modifyOrder("orderId", params, Constants.VARIETY_REGULAR)
kite.cancelOrder("orderId", Constants.VARIETY_REGULAR)
```

### Order status constants

```kotlin
Constants.ORDER_COMPLETE        // "COMPLETE"
Constants.ORDER_OPEN            // "OPEN"
Constants.ORDER_CANCELLED       // "CANCELLED"
Constants.ORDER_REJECTED        // "REJECTED"
Constants.ORDER_TRIGGER_PENDING // "TRIGGER PENDING"
```

---

## 8. Portfolio

```kotlin
kite.getHoldings()   // List<Holding> — long-term holdings (CNC)
kite.getPositions()  // Map<String, List<Position>> — keys: "net", "day"

// Convert intraday MIS position to overnight CNC
kite.convertPosition(
    "RELIANCE", Constants.EXCHANGE_NSE,
    Constants.TRANSACTION_TYPE_BUY, Constants.POSITION_DAY,
    Constants.PRODUCT_MIS, Constants.PRODUCT_CNC, 1
)
```

---

## 9. GTT — Good Till Triggered Orders

GTT orders persist on Zerodha's servers and trigger when price hits a threshold — no polling needed.

```kotlin
import com.zerodhatech.models.GTTParams

// Single-leg GTT (e.g. buy RELIANCE if it drops to 2700)
val gttParams = GTTParams().apply {
    tradingsymbol  = "RELIANCE"
    exchange       = Constants.EXCHANGE_NSE
    triggerType    = Constants.SINGLE          // "single"
    lastPrice      = 2850.0
    triggerValues  = listOf(2700.0)
    orders         = listOf(
        GTTParams.Order().apply {
            tradingsymbol   = "RELIANCE"
            exchange        = Constants.EXCHANGE_NSE
            transactionType = Constants.TRANSACTION_TYPE_BUY
            quantity        = 1
            orderType       = Constants.ORDER_TYPE_LIMIT
            product         = Constants.PRODUCT_CNC
            price           = 2700.0
        }
    )
}

val gtt = kite.placeGTT(gttParams)
println("GTT ID: ${gtt.id}")

kite.getGTTs()               // List<GTT>
kite.getGTT(gttId)           // GTT
kite.cancelGTT(gttId)        // GTT
```

---

## 10. WebSocket Ticker (Real-Time Streaming)

The `KiteTicker` streams real-time market data over WebSocket. This belongs in **`event-service`**, not `service`.

**Max 3,000 instruments per connection. Max 3 concurrent connections per API key.**

### Setup

```kotlin
import com.zerodhatech.ticker.KiteTicker

val ticker = KiteTicker(kite.accessToken, kite.apiKey)

ticker.setOnConnectedListener {
    val tokens = arrayListOf(408065L, 738561L)  // instrument_tokens
    ticker.subscribe(tokens)
    ticker.setMode(tokens, KiteTicker.modeFull)  // "full", "quote", or "ltp"
}

ticker.setOnTickerArrivalListener { ticks ->
    ticks.forEach { tick ->
        println("${tick.instrumentToken} → LTP: ${tick.lastTradedPrice}")
        // Full mode also gives: tick.depth, tick.openInterest, tick.ohlc, etc.
    }
}

ticker.setOnDisconnectedListener { code, reason, remote ->
    // log disconnection; reconnection is automatic if tryReconnection = true
}

ticker.setOnErrorListener(object : OnError {
    override fun onError(ex: Exception) { /* log */ }
    override fun onError(error: String) { /* log */ }
})

ticker.setOnOrderUpdateListener { order ->
    // Order postbacks arrive here as real-time events
}

// Reconnection config
ticker.setTryReconnection(true)
ticker.setMaximumRetries(10)
ticker.setMaximumRetryInterval(30)  // seconds

ticker.connect()
```

### Tick data fields (modeFull)

```kotlin
tick.instrumentToken      // Long
tick.lastTradedPrice      // Double — LTP in INR (already divided)
tick.volume               // Long
tick.ohlc                 // contains .open, .high, .low, .close
tick.openInterest         // Long (F&O)
tick.depth                // market depth: .buy / .sell → List<Depth>
tick.lastTradedTime       // Date
tick.exchangeTimestamp    // Date
```

### Subscription modes

| Mode constant | Bytes/tick | Data |
|---|---|---|
| `KiteTicker.modeLTP` | 8 | Instrument token + LTP only |
| `KiteTicker.modeQuote` | 44 | LTP, volume, OHLC, avg price |
| `KiteTicker.modeFull` | 184 | Quote + 5-level depth, OI, timestamps |

Use `modeLTP` for watchlist presence checks, `modeFull` for order book display.

---

## 11. Error Handling

```kotlin
import com.zerodhatech.kiteconnect.KiteException

try {
    kite.placeOrder(params, Constants.VARIETY_REGULAR)
} catch (e: KiteException) {
    when (e.code) {
        403  -> // TokenException — session expired, trigger re-login
        400  -> // InputException / OrderException / MarginException / HoldingException
        429  -> // Rate limit exceeded — back off and retry
        502,
        504  -> // NetworkException — OMS unreachable, retry with backoff
        500  -> // DataException — internal OMS error
        else -> // GeneralException / UserException
    }
    println("Kite error [${e.code}]: ${e.message}")
}
```

---

## 12. Rate Limits Summary

| Operation | Limit |
|---|---|
| Historical data | 3 req/s |
| Market quotes (`/quote`) | 1 req/s |
| All other REST | 10 req/s |
| Order placement | 200 orders/min, 3000 orders/day |
| Order modifications | Max 25 per order |
| HTTP 429 response | Back off and retry |

---

## 13. Module Placement Decisions

| Concern | Module | Rationale |
|---|---|---|
| `KiteConnect` client wrapper + auth | `core` | Shared by `service`, `cron-job`, `event-service` |
| `InstrumentCache` | `core` | Used everywhere that needs symbol→token lookup |
| Historical data fetch + ta4j bridge | `core` | Backtesting logic is domain-level |
| `KiteTicker` (WebSocket) | `event-service` | Long-running streaming — separate lifecycle |
| Token refresh cron (daily 6 AM) | `cron-job` | Re-login at token expiry boundary |
| REST endpoints exposing Kite data | `service` + `resources` | HTTP layer |

---

## 14. Dependencies to Add by Module

### `core/pom.xml`

```xml
<!-- Kite Connect Java SDK -->
<dependency>
    <groupId>com.zerodhatech.kiteconnect</groupId>
    <artifactId>kiteconnect</artifactId>
    <version>3.5.1</version>
</dependency>

<!-- ta4j — technical analysis + backtesting engine -->
<dependency>
    <groupId>org.ta4j</groupId>
    <artifactId>ta4j-core</artifactId>
    <version>0.17</version>
</dependency>
```

### `event-service/pom.xml`

```xml
<!-- KiteTicker uses WebSockets internally via OkHttp (bundled with SDK) -->
<!-- No extra dependency needed beyond kiteconnect from core -->
<dependency>
    <groupId>com.tradingtool</groupId>
    <artifactId>core</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Parent `pom.xml` — version property to add

```xml
<kiteconnect.version>3.5.1</kiteconnect.version>
<ta4j.version>0.17</ta4j.version>
```

---

## 15. Key API Constraints / Gotchas

| Constraint | Detail |
|---|---|
| Access token expiry | Every day at 6:00 AM IST — must re-login daily |
| `instrument_token` vs `exchange_token` | Use `instrument_token` for historical data and WebSocket; `exchange_token` is exchange-internal |
| Prices in historical data | Already in INR (not paise) — unlike WebSocket binary frames |
| WebSocket prices | In paise — divide by 100 for INR (divide by 10,000,000 for CDS currency pairs) |
| Instruments CSV | Refresh daily before markets open; large file (~10MB unzipped), cache in memory |
| Historical data `continuous=true` | Stitches across expired F&O contracts — useful for multi-year futures backtests |
| Bracket orders | `VARIETY_BO` constant exists but bracket orders are discontinued by Zerodha |
| GTT status values | `active`, `triggered`, `disabled`, `expired`, `cancelled`, `rejected`, `deleted` |
| Sandbox / paper trading | Not available — Kite Connect only connects to live markets |
