# RSI Momentum V1

## Summary

Strategy 1 is now implemented as a weekly portfolio-ranking module that reuses the existing `daily_candles` table and stores only the latest computed strategy snapshot in Redis.

The weekly universe is:
- the configured base preset, default `NIFTY_LARGEMIDCAP_250`
- plus all symbols currently present in the `stocks` watchlist table

The strategy ranks the full universe weekly, exposes a `top 20` board, and produces a `top 10` actionable holdings list.

## What Was Built

- Core ranking service: `RsiMomentumService`
- Strategy config loader: `RsiMomentumConfigService`
- Universe builder: preset universe union watchlist symbols
- Redis snapshot output for the latest weekly run
- API endpoints:
  - `GET /api/strategy/rsi-momentum/latest`
  - `POST /api/strategy/rsi-momentum/refresh`
- Weekly cron trigger job and GitHub Actions schedule

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
  - `topCandidates`: top 20 ranked names
  - `holdings`: top 10 actionable names
  - `rebalance`: `entries`, `exits`, `holds`

Rebalance logic:
- keep an existing holding only while it remains inside top 20
- replace dropped names with the highest-ranked non-held names
- equal-weight target sizing is returned as metadata only

## Refresh Behavior

- Uses the latest available candles from `daily_candles`
- If a symbol is missing or stale, only that symbol is backfilled from Kite
- Latest result is cached in Redis with a 7-day TTL
- Weekly schedule runs Friday after market close

## Default Config

Current defaults:
- `baseUniversePreset = NIFTY_LARGEMIDCAP_250`
- `candidateCount = 20`
- `holdingCount = 10`
- `rsiPeriods = [22, 44, 66]`
- `minAverageTradedValue = 10.0`
- `rebalanceDay = FRIDAY`
- `rebalanceTime = 15:40`

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
