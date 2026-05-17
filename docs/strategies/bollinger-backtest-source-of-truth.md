# Bollinger Backtest Source Of Truth

This document is the canonical definition of the **current** Bollinger backtest behavior implemented in code.

Endpoint: `POST /api/strategy/bollinger/backtest`

## Strategy Scope
- Timeframe: daily candles
- Universe: selected at runtime from UI (`universe`) or manual `symbols`
- Position side: long-only
- Position sizing: equal-slot capital (`capital / maxOpenPositions`)
- Style: squeeze breakout with phased stop management

## Config (Current)
```json
{
  "capital": 200000,
  "maxOpenPositions": 1,
  "fromDate": "2026-01-01",
  "toDate": "2026-05-16",
  "setupWindowDays": 5,
  "tightSqueezeTolerancePct": 12.0,
  "volumeMultiplier": 2.0,
  "breakEvenProfitPct": 2.0,
  "maxHoldDays": 999
}
```

## Indicator Definitions
- Bollinger Bands: BB(20, 2)
- Squeeze strictness:
  - baseline bandwidth = minimum bandwidth over last 60 days
  - squeeze day rule: `bandwidth <= baseline * (1 + tightSqueezeTolerancePct / 100)`
  - default `tightSqueezeTolerancePct = 12`
  - setup arms when any **3 consecutive** squeeze days are found in the setup window
- Volume ratio: `volume(today) / SMA(volume, 20)`

## Buy Logic
A buy requires setup + trigger.

### 1) Setup Arming Window
Setup is armed when a squeeze event happened within the last `setupWindowDays` bars.

### 2) Entry Trigger (must pass all)
On a day where setup is armed:
- Either **Fast Day-1 path** or **Standard 2-day path** must pass.
- RSI heat guard must pass:
  - Reject entry if RSI(14) > 68 on any of: today, yesterday, day-2.

Fast Day-1 path:
- `close(today) > bbUpper(today)`
- `((close(today) - close(yesterday)) / close(yesterday)) * 100 >= 8`
- `volume(today) / SMA20(volume, excluding today) >= 10`

Standard 2-day path:
- `close(today) > bbUpper(today)`
- `close(yesterday) > bbUpper(yesterday)`
- `close(today) > open(today)`
- `close(yesterday) > open(yesterday)`

No volume check is applied for the standard 2-day path.

If all pass, enter at `close(today)`.

## Sell Logic (for open position)
Exit is stop-first, with phased stop upgrades.

### Phase 1: Safety
- Initial stop at entry: structural low of setup window + entry day.

### Phase 2: Protection
- If `high(today) >= entryPrice * (1 + breakEvenProfitPct/100)`, move stop to break-even (`entryPrice`) if higher than current stop.

### Phase 3: Profit Trail
- If `high(today) > entryDayHigh`, activate trail logic.
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

## Pseudocode
```text
for each trading day:
  process exits for active positions
  process entries if slot available

ENTRY:
  if squeeze seen within setupWindowDays:
    if (
       close(today) > bbUpper(today)
       and close-vs-prev-close >= 8%
       and volume-vs-prev20avg >= 10x
    ) OR (
       close(today) > bbUpper(today)
       and close(yesterday) > bbUpper(yesterday)
       and close(today) > open(today)
       and close(yesterday) > open(yesterday)
    ):
      if RSI(14) on today/yesterday/day-2 has any value > 68:
        SKIP
      else:
      BUY at close

EXIT:
  if stop hit (open/low model): STOP_LOSS_PHASE_X
  else if holdBars >= maxHoldDays: MAX_HOLD at close
  else HOLD

STOP UPGRADES:
  if high >= entry * (1 + breakEvenProfitPct): stop = max(stop, entry)
  if high > entryDayHigh: stop = max(stop, previousDayLow)
```
