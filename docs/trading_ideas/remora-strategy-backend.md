# Remora Strategy Backend

## 1) What this strategy is trying to detect

Remora is a daily signal that tries to catch **quiet institutional activity**:

- **Unusually high volume** suggests large participation.
- **Muted price movement** suggests that flow is being absorbed without a visible breakout/breakdown yet.
- **Consecutive occurrence** reduces one-day noise.

In plain terms: if volume is consistently heavy but price stays relatively flat, someone large may be accumulating or distributing inventory in the background.

## 2) Exact signal rules (current implementation)

Source: `RemoraSignalCalculator`.

A signal is produced only if all of the following pass:

1. Minimum data:
- At least `22` daily bars (`20` for baseline + at least `2` candidate signal days).

2. Volume condition:
- `volumeRatio = todayVolume / avgVolume20d`
- `avgVolume20d` is computed from the **20 bars before today** (today is intentionally excluded from baseline).
- A bar qualifies only if `volumeRatio >= 1.5`.

3. Price condition:
- `priceChangePct = (close - prevClose) / prevClose * 100`
- A bar qualifies only if `abs(priceChangePct) <= 1.5`.

4. Type classification:
- `ACCUMULATION` if `priceChangePct >= -0.5`
- `DISTRIBUTION` if `priceChangePct < -0.5`

5. Consecutive-days logic:
- Starting from today, the calculator walks backward up to 10 bars.
- It counts only consecutive qualifying bars of the **same** type.
- Signal fires only if `consecutiveDays >= 2`.

Returned result is anchored to **today’s bar**:
- `signalType`
- `today volumeRatio`
- `today priceChangePct`
- `consecutiveDays`

## 3) End-to-end backend flow

### 3.1 On-demand & proactive execution

Signals are computed on-demand when the API is called if the data is missing or stale (older than the last market open).

- API: `IndicatorService` and `RemoraService` handle the on-demand logic.
- Logic: If it's after 9:15 AM IST and no signals exist for today, a scan is triggered automatically.
- Authentication: Uses the Kite token stored in the database.

### 3.2 Per-stock scan in `RemoraService`

For each tracked stock:

1. Wait `kiteRateLimitDelayMs` (default `350ms`).
2. Fetch daily historical candles from Kite for a 30-day calendar window.
3. Build TA4J `BarSeries`.
4. Run `RemoraSignalCalculator.compute(series)`.
5. If result exists, persist using `insertIfAbsent(...)`.
6. If inserted (new for today), send Telegram alert.
7. If already present for today, skip Telegram (idempotent re-run behavior).

Failure behavior:
- Errors are handled per stock (scan continues).
- At the end, scan fails hard only if **all** stock fetches failed.

## 4) Persistence model

Source: migration `001_remora_signals.sql`, `RemoraSignalWriteDao`.

Table: `remora_signals`

Stored fields:
- stock identity: `stock_id`, `symbol`, `company_name`, `exchange`
- signal metrics: `signal_type`, `volume_ratio`, `price_change_pct`, `consecutive_days`
- timing: `signal_date`, `computed_at`

Idempotency:
- Unique index on `(stock_id, signal_date)`.
- Insert uses `ON CONFLICT (stock_id, signal_date) DO NOTHING`.
- This guarantees at most one signal row per stock per date, even if cron re-runs.

## 5) Read/API path

Source: `RemoraSignalReadDao`, `WatchlistResource`.

Endpoint:
- `GET /api/watchlist/remora`
- Optional query: `?type=ACCUMULATION` or `?type=DISTRIBUTION`

Ordering:
- Newest signal dates first, then stronger persistence (`consecutive_days`), then stronger volume (`volume_ratio`).

Frontend (`RemoraPage`) reads this endpoint and supports ALL/ACCUMULATION/DISTRIBUTION filters.

## 6) Business intuition behind each rule

1. **20-day average volume baseline**
- Roughly one trading month of context.
- Excluding today avoids diluting the spike with the spike itself.

2. **1.5x volume threshold**
- Filters out normal fluctuation; keeps only material participation.

3. **Price move capped at 1.5%**
- Keeps focus on stealthy flow, not already-expanded moves.

4. **Accumulation vs distribution split around -0.5%**
- Flat-to-slightly-up days map to likely buying absorption.
- More negative flat-ish days map to likely selling absorption.

5. **Minimum 2 consecutive days**
- One day can be random; repeated behavior is more likely intentional flow.

6. **10-day backward cap**
- Limits stale pattern extension and bounds computational window.

## 7) Operational notes and current caveat

- Strategy is daily-only (not intraday timing).
- Thresholds are fixed globally; no per-symbol volatility adaptation yet.
- It does not explicitly model earnings/news days.
- Telegram alerts are best-effort and non-blocking.

Potential bug to review:
- `findRecent(days)` query in `RemoraSignalReadDao` uses:
  - `INTERVAL ':days days'`
- Because `:days` is inside quotes, bind substitution may not behave as intended in SQL interval arithmetic.
- This method is not currently used in the exposed API path, but should be corrected before reliance.

## 8) Summary

Remora is a conservative “hidden-flow” detector:

- high relative volume,
- low daily price displacement,
- repeated for at least two sessions,

then labeled as accumulation/distribution and stored once per stock per day for alerting and UI consumption.
