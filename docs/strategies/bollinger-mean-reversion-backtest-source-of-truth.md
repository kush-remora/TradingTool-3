# Bollinger Mean Reversion Backtest Source Of Truth

This document is the canonical definition of the Bollinger **mean-reversion** backtest behavior implemented in code.

Endpoint: `POST /api/strategy/bollinger/mean-reversion/backtest`

## Strategy Scope
- Timeframe: daily candles
- Universe: selected at runtime from UI (`universe`) or manual `symbols`
- Position side: long-only
- Position sizing: equal-slot capital (`capital / maxOpenPositions`)
- Style: lower-band stress mean reversion with phased stop management

## Config (Current)
```json
{
  "capital": 200000,
  "maxOpenPositions": 1,
  "fromDate": "2026-01-01",
  "toDate": "2026-05-16",
  "signalWindowDays": 5,
  "volumeMultiplier": 2.0,
  "bandwidthRecoveryThreshold": 0.75,
  "maxHoldDays": 999
}
```

## Indicator Definitions
- Bollinger Bands: BB(20, 2)
- Fast EMA: EMA(5)
- Volume ratio: `volume(today) / SMA(volume, 20)`

## Buy Logic
A buy requires setup + trigger + safety check.

### 1) Setup Arming Window
Setup is armed when a lower-band stress event happened within the last `signalWindowDays` bars:
- `close < bbLower`

### 2) Entry Trigger (must pass all)
On a day where setup is armed:
- Double Green day:
  - `close(today) > close(yesterday)`
  - `close(today) > open(today)`
- Any one trigger:
  - `close(today) > ema5(today)`
  - `volumeRatio20(today) >= volumeMultiplier`
  - `close(yesterday) > close(day-2)`
- Not still broken:
  - `close(today) > bbLower(today)`

If all pass, enter at `close(today)`.

### Initial stop selection (Phase 1)
- Structural stop: lowest low across setup window + entry day.
- Recovery exception:
  - `bandwidthRecovery = (entryPrice - bbLower) / (bbUpper - bbLower)`
  - if `bandwidthRecovery >= bandwidthRecoveryThreshold` (default `0.75`), use previous day's low as initial stop.

## Sell Logic (for open position)
Exit is stop-first, with phased stop upgrades.

### Phase 1: Safety
- Initial stop from rules above.

### Phase 2: Protection
- If `close(today) > bbMiddle(today)`, move stop to break-even (`entryPrice`) if higher than current stop.

### Phase 3: Profit Trail
- If `high(today) >= bbUpper(today)`, activate trail logic.
- Trail stop candidate is previous day low.
- Applied stop is non-loosening: `max(currentStop, previousDayLow)`.

### Stop execution model (intraday simulation using daily candles)
For active stop `S` on a day:
1. If `open <= S`, exit at `open` (gap-down behavior).
2. Else if `low <= S`, exit at `S`.
3. Else continue.

### Max hold fallback
- If `holdingBars >= maxHoldDays`, exit at `close(today)` with `MAX_HOLD`.

### End-of-backtest fallback
- Any still-open position is force-closed at the final available backtest candle close with `BACKTEST_END`.

## Exit Reason Labels
- `STOP_LOSS_PHASE_1`
- `STOP_LOSS_PHASE_2`
- `STOP_LOSS_PHASE_3`
- `MAX_HOLD`
- `BACKTEST_END`

## Operational Safety
- Symbols with insufficient candle history are skipped and returned in diagnostics.
- Symbol-level sync or indicator/state build failures are isolated; one symbol failure does not abort the full backtest.
