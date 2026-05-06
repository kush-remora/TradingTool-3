# Volume Burst Momentum V1

## Summary

This strategy tests a simple idea: when a stock has already traded an extreme amount of volume early in the day and is also showing strong price strength, the move may continue for the next few sessions.

Despite the trigger being detected intraday, this is not an intraday-only strategy. The current version is a short swing momentum setup with a hard profit target, a hard stop, and a maximum hold window of three trading days.

## Hypothesis

If a liquid stock reaches extreme cumulative volume intraday relative to its normal full-day volume, that day may represent a genuine participation shock rather than normal noise.

When that participation shock is combined with an already-strong price move, the stock may continue moving upward for the rest of the day or over the next one to three sessions.

## Signal Definition

Primary trigger:
- `volumeBurstRatio(T) = cumulativeVolumeTodayAtTimeT / averageFullDayVolumeLast20Sessions`
- trigger when `volumeBurstRatio(T) >= threshold`

Initial thresholds to test:
- `1.0`
- `1.5`
- `2.0`

Price-strength filter:
- stock return from previous close is between `+3%` and `+8%`

Optional context filters to test:
- price above VWAP at trigger time
- price above trigger-candle high before entry
- price above previous day high

## Trade Rules

Entry variants to compare:
- buy immediately when the trigger fires
- buy on break above the trigger candle high
- buy on a small pullback after the trigger

Exit rules:
- take profit at `+5%` from entry
- stop loss at `-2%` from entry
- maximum hold of `3 trading days`
- exit on whichever condition happens first

## Why This Is Different From Standard RVOL

This strategy does not compare 11:00 volume to average 11:00 volume.

Instead, it intentionally looks for a more extreme event:
- a stock whose cumulative intraday volume has already reached or exceeded its average full-day volume benchmark
- or even `2x` that full-day benchmark before the market closes

This is an event detector, not a normal relative-volume ranking model.

## Universe and Filters

Recommended starting universe:
- NSE cash equities only
- exclude very low-liquidity and low-price names

Recommended filters:
- minimum 20-day average traded value
- skip stocks near upper circuit
- track gap-up names separately from non-gap names

## Backtest Variants

Minimum variants to compare:
- `volumeBurstRatio >= 1.0`
- `volumeBurstRatio >= 1.5`
- `volumeBurstRatio >= 2.0`

- entry at trigger
- entry on trigger-candle breakout
- entry on pullback

- no trend filter
- above-VWAP filter

## Reporting Requirements

Report at least:
- number of trades
- win rate
- average gain
- average loss
- expectancy
- max drawdown
- profit after brokerage and slippage

Break out outcomes by:
- target hit same day
- target hit on day 2
- target hit on day 3
- stopped intraday
- stopped after overnight gap
- timed out at 3-day max hold

Also split results by:
- trigger threshold
- trigger time bucket
  - before 10:00
  - 10:00 to 12:00
  - after 12:00
- gap-up open versus normal open

## Risks and Failure Modes

- The trigger may fire late, leaving too little move left.
- News-driven spikes may reverse sharply after initial excitement.
- The `-2%` stop is not guaranteed overnight because gap-down opens can skip past the stop level.
- Small-cap speculative names may dominate results unless liquidity filters are strict.

## Out of Scope

- automatic live order placement
- short selling
- options or futures overlays
- same-time-of-day RVOL logic for this version

## Current Product Call

This is worth testing exactly as observed in practice:
- cumulative intraday volume against average full-day volume
- fixed `+5%` target
- fixed `-2%` stop
- maximum `3-day` hold

The main goal of the first backtest is not to optimize every parameter. It is to answer one clean question:

Does this extreme intraday volume burst create real short-swing continuation after costs and overnight gap risk?
