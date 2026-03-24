/mo# Watchlist Dashboard — Implementation Plan

**Status:** Pending confirmation
**Date drafted:** 2026-03-24

---

## What We're Building

A general-purpose watchlist table that answers 5 trader questions for every stock:

| # | Question | Category |
|---|---|---|
| 1 | Where is the price right now? | Identity |
| 2 | Is the trend up or down? | Direction |
| 3 | How fast is it moving? | Momentum |
| 4 | Is it stretched or due for a bounce? | Mean Reversion |
| 5 | Is there conviction behind the move? | Volume/Strength |

---

## Proposed Table Columns

| Group | Column | What it tells you | Source |
|---|---|---|---|
| — | Symbol / Sector | Identity + sector rotation | Static |
| PRICE | LTP | Current price | Live |
| PRICE | Change % (1D) | Today's mood | Live |
| TREND | SMA 50 | Medium-term direction | Calculated |
| TREND | SMA 200 | Long-term structure | Calculated |
| TREND | Price vs 200MA | Above = bullish, Below = bearish | Derived |
| MOMENTUM | ROC (1W) | Short-term speed | ta4j |
| MOMENTUM | ROC (3M) | Trend confirmation | ta4j |
| MOMENTUM | MACD Signal | Bullish / Bearish / Crossover | ta4j |
| MEAN REVERSION | RSI (14) | Overbought >70, Oversold <30 | ta4j |
| RISK | Drawdown % | How far from recent peak | ta4j |
| RISK | Max DD (1Y) | Worst historical drop | ta4j |
| CONVICTION | Volume vs Avg | Is today's move backed by volume? | Derived |
| RANKING | Sector Rank | Is institutional money flowing in? | Derived |

### Minimum Viable Start (8 columns)
`Symbol → Change % → Price vs 200MA → RSI → ROC 3M → MACD Signal → Drawdown % → Volume vs Avg`

### Color-Coding Rules

| Column | Green | Yellow | Red |
|---|---|---|---|
| Price vs 200MA | Above | Within 2% | Below |
| RSI | 40–65 | 65–75 | >75 or <30 |
| ROC 3M | Positive | Near 0 | Negative |
| MACD Signal | Bullish crossover | Flat | Bearish crossover |
| Drawdown % | < -5% | -5% to -15% | > -15% |
| Volume vs Avg | > 1.5x | 0.8–1.5x | < 0.8x |

---

## Architectural Design

### Data Volatility Classification

| Data | Changes How Often | Strategy |
|---|---|---|
| LTP, Change %, Today's Volume | Every second (live market) | Never cache — inject live at response time |
| Today's OHLC | Once per day at market open | Cache per trading day |
| SMA 50/200, ROC 1W/3M, RSI 14, MACD, Drawdown, Avg Volume | Once per day (computed from daily history) | Cache until next trading day (9:15 AM refresh) |
| Max DD (1Y) | Drifts weekly | Cache 7 days |
| Sector classification | Rarely changes | Already in `InstrumentCache`, refresh daily |

### Two-Tier In-Memory Cache

```
┌───────────────────────────────────────────────────────┐
│  Tier 1: HistoricalDataCache                          │
│  Key:   instrument_token (Long)                       │
│  Value: List<Candle> — 1 year of daily OHLCV          │
│  TTL:   Refreshed once daily at 9:15 AM IST cron      │
│  Size:  365 bars × 100 stocks = ~36,500 rows (tiny)   │
└───────────────────────────────────────────────────────┘
          ↓ ta4j computes on first request or cron
┌───────────────────────────────────────────────────────┐
│  Tier 2: IndicatorCache                               │
│  Key:   instrument_token (Long)                       │
│  Value: ComputedIndicators                            │
│         (sma50, sma200, rsi14, roc1w, roc3m,          │
│          macdSignal, drawdownPct, maxDd1y, avgVol20d)  │
│  TTL:   Invalidated when Tier 1 refreshes             │
│  Cost:  ta4j runs in <1ms per stock                   │
└───────────────────────────────────────────────────────┘
          ↓ merged at API response time
┌───────────────────────────────────────────────────────┐
│  Live Injection Layer                                  │
│  kite.getLTP(all watchlist tokens)                     │
│  → 1 API call for up to 1000 tokens                   │
│  → merged into WatchlistRow at response time           │
│  → frontend polls this endpoint every 10–30 seconds   │
└───────────────────────────────────────────────────────┘
```

**Why no persistent table for indicators?**
Indicators are fully re-derivable from the 1-year OHLCV history in Tier 1.
Storing them in DB would duplicate what ta4j computes in <1ms per stock.

### Rate Limit Budget (for 100 stocks)

| Operation | API calls needed | Time cost | When |
|---|---|---|---|
| Fetch 1yr daily candles | 100 calls at 3 req/s | ~34 seconds | Once at 9:15 AM cron (background) |
| LTP poll per refresh | 1 call (getLTP limit: 1000) | instant | Every 10–30s during market hours |
| OHLC refresh | 1 call (getOHLC limit: 1000) | instant | Once at 9:16 AM cron |

The 34-second startup is acceptable — it runs in a background thread. API returns cached data (or `{ status: "loading" }`) until fresh.

---

## Strategy-Extensible Architecture

The indicator cache produces **raw, strategy-agnostic data**. Strategy scorers are pure functions on top.

```
IndicatorService
  → computes WatchlistRow[] (generic, no strategy logic)
       │
       ├── Alpha10MomentumScorer
       │     composite score = 60% × Avg RSI(22,44,66) + 40% × Avg ROC(1M,3M,6M)
       │     → filters + ranks → top 5 picks
       │
       ├── Alpha10MeanReversionScorer
       │     oversold score = 50% × RSI percentile + 30% × % below 20SMA + 20% × vol ratio
       │     → 6-filter gate + ranks → top 5 picks
       │
       └── [future] MomentumScreener, RSIScreener, WeekendInvesting...
```

Each scorer implements a simple interface:
```kotlin
interface StrategyScorer {
    val strategyId: String
    fun score(rows: List<WatchlistRow>): List<ScoredStock>
}
```

New strategies = add one class. No changes to data layer.

---

## Implementation Phases

### Phase 1 — Backend: Data Layer (`core/`)

Files to create:
- `ComputedIndicators.kt` — data class: sma50, sma200, rsi14, roc1w, roc3m, macdSignal, drawdownPct, maxDd1y, avgVol20d, computedAt
- `HistoricalDataCache.kt` — `ConcurrentHashMap<Long, List<Candle>>` with refresh timestamp
- `IndicatorCache.kt` — `ConcurrentHashMap<Long, ComputedIndicators>` with TTL stamp
- `IndicatorService.kt` — orchestrates: fetch history → compute via ta4j → populate both caches; also merges live LTP into `WatchlistRow`

### Phase 2 — Backend: API Layer (`service/resources/`)

Files to create:
- `WatchlistRow.kt` — response model: symbol, exchange, ltp, changePercent, sma50, sma200, priceVs200maPct, rsi14, roc1w, roc3m, macdSignal, drawdownPct, maxDd1y, volumeVsAvg, sector
- `WatchlistIndicatorResource.kt`
  - `GET /watchlist/indicators` → `List<WatchlistRow>` (cached indicators + live LTP injected)
  - `POST /watchlist/indicators/refresh` → triggers background cache rebuild

### Phase 3 — Backend: Cron Integration (`cron-job/`)

- Update existing cron-job to call `IndicatorService.refreshAll()` at 9:15 AM IST daily
- Refresh runs as a background coroutine; stale cache served until fresh data is ready

### Phase 4 — Frontend (`frontend/src/`)

Files to create:
- `pages/WatchlistDashboard.tsx` — Ant Design `Table` with color-coded cells, auto-refreshes every 10s
- `hooks/useWatchlistIndicators.ts` — polls `GET /watchlist/indicators`, manages loading/error state
- `utils/indicatorColors.ts` — pure color-coding functions per the spec above
- Update `App.tsx` to add new Dashboard tab alongside existing Watchlist page

---

## What Is NOT Changing

- Existing `WatchlistPage.tsx` (stock add/edit/delete) — unchanged
- `InstrumentCache.kt` — unchanged
- `KiteConnectClient.kt` — unchanged
- DB schema — no new tables

---

## Open Questions (Need Confirmation Before Starting)

1. **LTP source**: Use **polling** (`getLTP()` every 10s) or **WebSocket** (`KiteTicker`)? Polling is simpler; WebSocket is real-time but adds lifecycle complexity.
2. **Frontend layout**: New tab alongside existing watchlist, or replace the sidebar view?
3. **In-memory cache OK**: Indicators are lost on service restart (rebuilt in ~34s). Is that acceptable?

---

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Kite not authenticated at startup → cache empty | Medium | Return `{ status: "loading" }` until authenticated |
| 100 stocks × 34s initial load | Low | Background thread; stale cache served in interim |
| ta4j needs ≥14 bars for RSI, ≥500 for percentile | Low | Guard with bar count check; skip percentile if insufficient |
| Frontend polling 10s × getLTP = 1 req/s budget used | Low | All 100 stocks in 1 getLTP call — well within limits |
