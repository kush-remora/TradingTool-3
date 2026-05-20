# 104-Week Breakout Backtest

## Overview
This feature adds a dedicated backtest flow for a breakout strategy using index-based universe selection and optional manual symbol filtering.

The strategy is available in Console UI as:
- `104-Week High Backtest`

## What We Built

### 1) Backend strategy engine
Implemented in:
- `core/src/main/kotlin/com/tradingtool/core/strategy/fiftytwohigh/FiftyTwoWeekHighBacktestService.kt`
- `core/src/main/kotlin/com/tradingtool/core/strategy/fiftytwohigh/FiftyTwoWeekHighBacktestModels.kt`

Core behavior:
- Signal: breakout above prior `104-week` high (implemented as `504` trading-day lookback, excluding current day).
- Entry: next-day open.
- Exit: when candle high reaches `entry * (1 + targetProfitPct/100)`.
- If target never hits by backtest end: position remains `OPEN`.
- One open position at a time per symbol.
- Re-entry cooldown enforced after each entry.

### 2) API endpoints
Implemented in:
- `resources/src/main/kotlin/com/tradingtool/resources/StrategyResource.kt`

Endpoints:
- `GET /api/strategy/52-week-high/universes`
  - Returns available `index_key` options and counts from `public.index_constituents`.
- `POST /api/strategy/52-week-high/backtest`
  - Runs strategy and returns summary + row-level trades.

### 3) Frontend UI
Implemented in:
- `frontend/src/pages/FiftyTwoWeekHighBacktestPage.tsx`
- `frontend/src/hooks/use52WeekHighBacktest.ts`
- `frontend/src/App.tsx`
- `frontend/src/types.ts`

UI controls:
- Index multi-select (from `index_constituents` universe API)
- Optional manual symbols
- Configurable target profit percentage

Result table columns:
- Symbol
- Index bucket
- Enter trade date
- Exit trade date (or OPEN)
- Holding days
- Status

Table UX improvements:
- All columns sortable
- All columns filterable (search dropdown)

## Hardcoded Strategy Constraints (Current)

These are intentionally fixed server-side:
- Cooldown: `180` days
- Lookback: `504` trading days (`104 weeks`)
- Backtest window: `365` days (entry-eligible period)
- History fetch window: `1300` days (buffer for holidays/missing sessions)

Notes:
- `targetProfitPct` remains configurable from UI.
- `historyDays` and `cooldownDays` sent by UI are ignored by backend in favor of hardcoded values.

## Universe and Bucketing
Universe source:
- `public.index_constituents`

Bucket classification (display):
- `LARGE` if membership key contains `LARGE`
- `MID` if membership key contains `MID`
- `SMALL` if membership key contains `SMALL`
- `OTHER` otherwise

## Caching and Data Loading Improvements

### Daily candle cache
Updated `CandleCacheService` to use range-aware keys:
- `candles:<SYMBOL>:day:<from>:<to>`

Benefit:
- Reduces repeated cache misses for repeated same-range backtest runs.

### Backfill trigger hardening
In 104-week backtest service:
- Backfill runs only if candles are empty, or latest candle gap is greater than a small tolerance.
- Prevents repeated unnecessary sync attempts for recently listed stocks or small market-date gaps.

## RCA Findings We Addressed

### Why trades appeared later than expected (e.g., MARUTI)
Primary cause observed:
- 104-week lookback needs deeper data. If effective history is too tight, first eligible signal can be delayed.

Fix applied:
- Increased hardcoded history window to `1300` days for robust lookback coverage.

### Why some expected dates were not entered (e.g., BHARTIARTL April vs November)
Primary cause observed:
- Entry eligibility is restricted to the backtest period (last 365 days from selected `toDate`).
- Signals outside the entry window are intentionally ignored.

## Validation Performed
- `mvn -pl core,resources -DskipTests compile` -> SUCCESS
- `npm --prefix frontend run build` -> SUCCESS

## How to Use
1. Open Console -> `104-Week High Backtest`.
2. Select one or more index keys.
3. Optionally paste manual symbols to narrow scope.
4. Set target profit %.
5. Run backtest.
6. Use table filters/sorters to inspect results.

## Future Extensions (Optional)
- Add CSV export for this page.
- Add explicit debug columns for signal candle date and prior 104-week high value.
- Add tests for edge cases: holiday gaps, exact cooldown boundary, and late-listing symbols.
