# Caching Architecture — Multi-Watchlist with Render Free Tier

**Context:**
- Render free tier = server restart every 15 minutes
- Separate processes: `service` (REST) and `cron-job` (scheduled)
- Multiple watchlists via tags (e.g., "alpha10-momentum", "netweb-swing", "all")
- Need cross-process data sharing + persistence through restarts

---

## Problem: Pure In-Memory Cache Doesn't Work

In my original plan, I proposed:
- IndicatorCache (in-memory ConcurrentHashMap in `service`)
- Refreshed by cron-job at 9:15 AM

**But this fails because:**

1. **Separate processes**: `cron-job` runs independently. It can't populate the `service` process's memory.
2. **15-min restart cycle**: `service` restarts every 15 minutes. Cache is lost unless rebuilt.
3. **Cron-job also restarts**: The cron-job process exits after finishing its task. It doesn't hold a long-lived cache.

Result: Cache invalidated on every restart = constant re-fetching from Kite API (wasteful).

---

## Data Classification for Render's Constraints

### What needs cross-process sharing?

| Data | Fresh by | Must survive restart? | Best storage |
|---|---|---|---|
| **Historical candles (1yr)** | Daily (9:15 AM cron) | Yes | Redis or DB |
| **Computed indicators** (SMA, RSI, ROC, etc.) | Daily (9:15 AM cron) | Yes | Redis or DB |
| **Live LTP** | Every 1-10s | No (ephemeral) | Local cache |
| **Stock list + tags** | Rarely | Yes | DB (already there) |

---

## Three Caching Options

### Option 1: Persistent DB Only (Simplest)

```
DB Schema:
┌─ stocks (existing)
├─ stock_indicators (new)
│  ├─ id, instrument_token, tag
│  ├─ sma50, sma200, rsi14, roc1w, roc3m, macd_signal, drawdown_pct, max_dd_1y, avg_vol_20d
│  ├─ computed_at (timestamp)
│  └─ indexed: (instrument_token, tag, computed_at)
```

**Flow:**
```
9:15 AM cron-job:
  1. SELECT all stocks
  2. FOR EACH stock: fetch 1yr candles, compute indicators
  3. UPSERT INTO stock_indicators (computed_at = NOW)

Service on request:
  1. SELECT * FROM stock_indicators WHERE tag = ? AND computed_at > NOW - 1 day
  2. If exists: return from DB
  3. If missing/stale: return { status: "loading" }

Service startup:
  → Pre-warm local cache from DB (if recent indicators exist)
  → On next request, serve from local memory
```

**Pros:**
- Simple, no new dependencies (Redis)
- Persists through restarts automatically
- Single source of truth

**Cons:**
- Every request hits DB (slower than in-memory)
- More DB load

---

### Option 2: Redis Only (Fast but Ephemeral)

```
Redis Keys:
  watchlist:{tag}:indicators → serialized List<ComputedIndicators>
  watchlist:{tag}:computed_at → timestamp
  stock_ltp:{token} → LTP (from live polling)

TTL: 24 hours for indicators, 30s for LTP
```

**Flow:**
```
9:15 AM cron-job:
  1. SELECT all stocks
  2. FOR EACH stock: fetch 1yr candles, compute indicators
  3. FOR EACH tag: SET Redis watchlist:{tag}:indicators (TTL 24h)

Service on request:
  1. GET watchlist:{tag}:indicators FROM Redis
  2. If hit: return cached
  3. If miss/expired: return { status: "loading" }

Service restart:
  → Redis persists (separate service), cache is still warm
  → Immediate availability after restart ✓
```

**Pros:**
- Fast (in-memory, sub-ms latency)
- Survives service restarts ✓
- Perfect for 15-min restart cycle

**Cons:**
- Adds Redis dependency (need to manage on Render)
- Cron-job failure = stale cache until next 9:15 AM
- If Redis restarts, cache is gone (but can be rebuilt from DB)

---

### Option 3: Redis + DB (Hybrid — Recommended)

```
Layer 1: Redis (L1 cache)
  watchlist:{tag}:indicators → computed data (TTL 24h)

Layer 2: DB (L2 fallback + persistent log)
  stock_indicators table → full record with computed_at
```

**Flow:**
```
9:15 AM cron-job:
  1. SELECT all stocks
  2. FOR EACH stock: fetch 1yr candles, compute indicators
  3. WRITE TO BOTH:
     - Redis: watchlist:{tag}:indicators (TTL 24h) ✓ fast
     - DB: INSERT/UPDATE stock_indicators (computed_at = NOW) ✓ persistent

Service on request:
  1. TRY: GET watchlist:{tag}:indicators FROM Redis (fast hit)
     → return from Redis
  2. IF MISS: GET FROM DB (slower, but still fresh if cron ran)
     → return from DB
  3. IF BOTH MISS: return { status: "loading" }

Service startup:
  → Redis may be cold, but DB has recent data
  → Pre-warm Redis from DB on startup
  → Subsequent requests hit Redis (fast)
```

**Pros:**
- Fast during normal operation (Redis L1)
- Survives both service AND Redis restarts (DB L2)
- No data loss if cron runs successfully
- Optimal for Render free tier

**Cons:**
- Slight complexity (two-tier fallback logic)
- Both systems must stay in sync

---

## Recommendation: Option 3 (Redis + DB)

**Why:**
1. Redis handles the 15-min restart cycle gracefully
2. DB provides safety net if Redis crashes
3. Acceptable trade-off: complexity vs. reliability
4. Scales well to 50–100 stocks

**Implementation sketch:**

```kotlin
// Service-side lookup
fun getWatchlistIndicators(tag: String): WatchlistResponse {
    // L1: Redis
    val cached = redis.get("watchlist:$tag:indicators")
    if (cached != null && isFresh(cached)) {
        return WatchlistResponse.cached(cached)
    }

    // L2: DB
    val fromDb = db.query("SELECT * FROM stock_indicators WHERE tag = ? AND computed_at > ?", tag, cutoff)
    if (fromDb.isNotEmpty()) {
        redis.set("watchlist:$tag:indicators", fromDb, TTL_24H)  // repopulate L1
        return WatchlistResponse.cached(fromDb)
    }

    // Both miss = stale, return loading
    return WatchlistResponse.loading()
}
```

---

## Cron-Job Schedule & Flow

```
Daily at 9:15 AM IST:

cron-job process:
  ├─ Connect to Supabase (stocks + tags)
  ├─ Connect to Redis (cache write)
  ├─ Connect to Kite API (historical data)
  │
  ├─ PHASE 1: GROUP stocks by tags
  │  (e.g., { "alpha10-momentum": [RELIANCE, INFY, ...], "all": [...] })
  │
  ├─ PHASE 2: Fetch historical data + compute indicators
  │  FOR EACH stock:
  │    1. Kite.getHistoricalData(token, "1yr") → List<Candle> (cached in Redis tier 1)
  │    2. ta4j compute(candles) → ComputedIndicators
  │    3. Store in batches
  │  [Throttled at 3 req/s per Kite limit]
  │
  ├─ PHASE 3: Write to Redis (fast, TTL 24h)
  │  FOR EACH tag:
  │    Redis.SET("watchlist:{tag}:indicators", indicators, TTL_24H)
  │    Redis.SET("watchlist:{tag}:computed_at", NOW)
  │
  ├─ PHASE 4: Write to DB (persistent)
  │  FOR EACH stock:
  │    DB.INSERT/UPDATE stock_indicators {
  │      instrument_token, tag, indicators_json, computed_at
  │    }
  │
  └─ EXIT (process lifecycle ends)
```

---

## Cache Framework Choice

Given the hybrid architecture:

| Framework | Use case |
|---|---|
| **Redis client**: `jedis` or `lettuce` | L1 cache (cross-process) |
| **Supabase/PostgreSQL** | L2 fallback + persistent store |
| **Local Caffeine** (optional) | L0 for live LTP polling (process-local) |
| **ConcurrentHashMap** | Session/token state only |

**Recommended dependency:**
```xml
<!-- Redis client with connection pooling -->
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
    <version>5.0.0</version>
</dependency>

<!-- Alternative: async client (works with Kotlin coroutines) -->
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
    <version>6.3.0</version>
</dependency>
```

Use **Lettuce** if adding async/coroutine support. Use **Jedis** if keeping blocking API.

---

## Multiple Watchlists via Tags

Your existing `stocks.tags` column groups stocks into watchlists:

```
Stocks:
  RELIANCE    → tags: ["alpha10-momentum", "all"]
  INFY        → tags: ["alpha10-momentum", "all"]
  HDFC BANK   → tags: ["netweb-swing", "all"]
  TCS         → tags: ["all"]

API:
  GET /watchlist/indicators?tag=alpha10-momentum
    → returns RELIANCE + INFY indicators

  GET /watchlist/indicators?tag=netweb-swing
    → returns HDFC BANK indicators

  GET /watchlist/indicators?tag=all
    → returns all stocks
```

**Cron-job computes once per tag**, so no duplication.

---

## Open Questions for You

1. **Redis on Render**: Are you OK with adding Redis? Render free tier supports Redis add-on ($5/mo for hobby tier, free for small projects). Or prefer DB-only (Option 1)?

2. **Tag inheritance**: Should a stock tagged "alpha10-momentum" be included in "all", or separate queries?

3. **Partial refresh**: If cron fails or a new stock is added mid-day, should there be a manual `/refresh` endpoint to re-fetch just that stock?

4. **Candle history cache**: Should 1-year candles also be cached in Redis (large), or just computed indicators? (Candles = ~365KB per stock × 100 stocks = 36MB, manageable in Redis)
