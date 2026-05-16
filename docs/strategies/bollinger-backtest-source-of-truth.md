# Bollinger Backtest Source Of Truth

This document is the canonical definition of the current Bollinger backtest behavior.

## Strategy Scope
- Timeframe: daily candles
- Universe: selected at runtime from UI (`universe`) or manual `symbols`
- Position side: long-only
- Position sizing: equal-slot capital (`capital / maxOpenPositions`)

## Config (Current)
```json
{
  "capital": 200000,
  "maxOpenPositions": 1,
  "fromDate": "2026-01-01",
  "toDate": "2026-05-16",
  "signalWindowDays": 5,
  "entryRsiMax": 30,
  "takeProfitPct": 5,
  "stopLossPct": 2,
  "maxHoldDays": 999
}
```

## Indicator Definitions
- Bollinger Bands: BB(20, 2)
- RSI: RSI(14)

## Buy Logic
A buy needs two phases: setup and trigger.

### 1) Setup Arming Window
For each symbol/day, track whether these happened within the last `signalWindowDays` bars:
- Lower-band event: `close < bbLower`
- RSI oversold event: `rsi14 <= entryRsiMax`

Setup is armed only if both events are recent enough (within window), even if they occur on different days.

### 2) Entry Trigger (must pass all)
On a day where setup is armed:
- Two-day close confirmation:
  - `close(today) > close(yesterday)`
  - `close(yesterday) > close(day-2)`
- `close(today) > bbLower(today)`
- RSI rising: `rsi14(today) > rsi14(yesterday)`

If all pass, enter at `close(today)`.

## Sell Logic (for open position)
Exit checks run in strict priority order.

### Exit Priority
1. `STOP_LOSS`
- If `close(today) <= stopPrice`
- `stopPrice = entryPrice * (1 - stopLossPct/100)`

2. `TAKE_PROFIT`
- If `high(today) >= takeProfitPrice`
- `takeProfitPrice = entryPrice * (1 + takeProfitPct/100)`

3. `REVERSAL_EXIT` (window + trigger)
- First arm exit setup window with both recent events:
  - Upper-band event: `high >= bbUpper`
  - RSI overbought event: `rsi14 >= 70`
- Then trigger exit when both are true:
  - `close(today) < close(yesterday)`
  - `close(yesterday) < close(day-2)`
  - `rsi14(today) < rsi14(yesterday)`
- Exit price: `close(today)`

4. `MAX_HOLD`
- If holding bars `>= maxHoldDays`
- Exit price: `close(today)`

## Pseudocode
```text
for each trading day:
  process exits for active positions in priority order
  then process new entries if slot available

ENTRY:
  update setup markers (lower-band close break, RSI <= entryRsiMax)
  if setup armed within signalWindowDays:
    if two-day-up-close + close>bbLower + RSI rising:
      BUY at close

EXIT:
  if close <= stopPrice: STOP_LOSS
  else if high >= takeProfitPrice: TAKE_PROFIT
  else if exit setup armed and two-day-down-close and RSI falling: REVERSAL_EXIT
  else if holdDays >= maxHoldDays: MAX_HOLD
  else HOLD
```

## Notes
- Setup conditions can occur on different days; they do not need same-day coincidence.
- Trigger conditions are evaluated on the current bar with day-1/day-2 context.
- If data is insufficient for a symbol, it is skipped and listed in diagnostics.
