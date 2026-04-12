# RSI Momentum V1

## Summary

Strategy 1 is now implemented as a weekly portfolio-ranking module that reuses the existing `daily_candles` table and stores only the latest computed strategy snapshot in Redis.

It now also includes a non-live calibration workflow that backtests RSI period sets per universe profile and writes the selected `rsiPeriods` back to `rsi_momentum_config.json`.

The weekly universe is:
- the configured base preset, default `NIFTY_LARGEMIDCAP_250`
- plus all symbols currently present in the `stocks` watchlist table

The strategy ranks the full universe weekly, exposes a `top 40` board, and produces a `top 10` actionable holdings list.

## What Was Built

- Core ranking service: `RsiMomentumService`
- Strategy config loader: `RsiMomentumConfigService`
- Universe builder: preset universe union watchlist symbols
- Redis snapshot output for the latest weekly run
- API endpoints:
  - `GET /api/strategy/rsi-momentum/latest`
  - `POST /api/strategy/rsi-momentum/refresh`
- Weekly cron trigger job and GitHub Actions schedule
- Calibration CLI:
  - `com.tradingtool.cron.RsiMomentumCalibrationJobKt`
  - writes report artifacts under `build/reports/rsi-momentum-calibration/latest.{json,md}`

## Data Model

- `Postgres`: existing `daily_candles` remains the durable source of truth
- `Redis`: latest RSI momentum snapshot only
- No new Postgres database
- No new Postgres strategy table in V1

## Universe Rules

- Start from `NIFTY_LARGEMIDCAP_250`
- Union all watchlist symbols from `stocks`
- Resolve tokens through `InstrumentCache`
- Exclude unresolved symbols
- Exclude illiquid names using configured minimum average traded value

## Ranking Rules

- Compute `RSI(22)`, `RSI(44)`, `RSI(66)`
- Rank descending by average RSI
- Return:
  - `topCandidates`: top 40 ranked names
  - `topCandidates.avgRsi`: visible score for all 40 board rows
  - `holdings`: top 10 actionable names
  - `rebalance`: `entries`, `exits`, `holds`
  - `topCandidates.entryAction`: `ENTRY`, `HOLD`, `WATCH_PULLBACK`, `SKIP`, `WATCH`
  - `topCandidates.buyZoneLow10w` / `buyZoneHigh10w`: 10-week low/high zone
  - `topCandidates.lowestRsi50d` / `highestRsi50d`: 50-day RSI bounds

Rebalance logic:
- keep an existing holding only while it remains inside top 20
- replace dropped or skipped names using eligible names from top 40 replacement pool
- for new entries only:
  - `WATCH_PULLBACK` when price is above `SMA20 + 20%`
  - `SKIP` when price is above `SMA20 + 30%`
- equal-weight target sizing is returned as metadata only

## Calibration Rules

- Runs weekly backtest on `daily_candles` using the same ranking and rebalance rules as live strategy:
  - Top 20 rebalance buffer
  - Top 40 display/replacement pool
  - extension skip logic for new entries
- Optimizes parameter sets by annualized Sortino (risk-adjusted objective)
- Applies guardrails:
  - max drawdown
  - turnover
  - first-half vs second-half Sortino stability
- If top sets are close, prefers lower turnover and simpler periods
- If no candidate passes all guardrails, selects the top Sortino candidate and marks rejection reasons in report

## Refresh Behavior

- Uses the latest available candles from `daily_candles`
- If a symbol is missing or stale, only that symbol is backfilled from Kite
- Latest result is cached in Redis with a 7-day TTL
- Weekly schedule runs Friday after market close

## Default Config

Current defaults:
- `profiles[largemidcap250].baseUniversePreset = NIFTY_LARGEMIDCAP_250`
- `profiles[largemidcap250].rsiPeriods = [22, 44, 66]`
- `profiles[smallcap250].baseUniversePreset = NIFTY_SMALLCAP_250`
- `profiles[smallcap250].rsiPeriods = [63, 126, 252]`
- `candidateCount = 20`
- `boardDisplayCount = 40`
- `replacementPoolCount = 40`
- `holdingCount = 10`
- `minAverageTradedValue = 10.0`
- `maxExtensionAboveSma20ForNewEntry = 0.20`
- `maxExtensionAboveSma20ForSkipNewEntry = 0.30`
- `rebalanceDay = FRIDAY`
- `rebalanceTime = 15:40`
- calibration metadata per profile:
  - `rsiCalibrationRunAt`
  - `rsiCalibrationMethod`
  - `rsiCalibrationSampleRange`

Config file:
- `rsi_momentum_config.json`

Universe file:
- `core/src/main/resources/strategy-universes/nifty_largemidcap_250.csv`

## Validation

Covered by tests:
- universe union without duplicates
- unresolved symbol filtering
- ranking order
- holding retention inside candidate buffer
- exit handling when a holding falls outside top candidates

Targeted verification run:

```bash
mvn -q -pl core,resources,service,cron-job test
```

## Out of Scope in V1

- broker execution
- market regime filter
- fundamental quality overlay
- cash-switch logic
- historical weekly snapshot archive
- frontend strategy page
