# Weekly Low Limit-Entry Backtest

## Working Understanding — 2026-08-01

Kush wants a simple six-month backtest for either one NSE stock or a selected watchlist. For every completed trading week, the engine places a hypothetical long limit order 1% above that week's low for the following trading week. If the limit is not touched during the following week, the setup is recorded as `NO_FILL` and ignored. If filled, the trade has a fixed +5% target and -5% stop loss measured from the actual fill price. This is a price-only mean-reversion experiment, not yet a Wyckoff accumulation detector.

## Proposed V1 Contract

- Use daily OHLC candles and the latest six completed calendar months.
- `Previous-week low` is the minimum daily low from the immediately preceding completed NSE trading week. The limit order is `previous-week low × 1.01`.
- Each trade also reports the exact daily candle date on which that previous-week low occurred.
- Before placing the following week's order, skip the setup when the previous week's final close is already below the adjusted limit. This is recorded as `PREMARKET_FILTER_SKIP` because the decision is made before the entry week begins.
- When a candidate entry candle touches the limit, skip it if that day's open is more than 1% above or below the limit. This is recorded as `OPEN_DEVIATION_SKIP` and is intended to avoid large gap-through entries.
- Each trade has a validation page covering the previous-low date, entry week, and five forward trading sessions with daily OHLC and close-to-close percentage change.
- The UI exposes two explicit rule variants:
  - `ANY_DAY_MAX_5_TRADING_DAYS`: the order is active on any day of the next week; after entry, hold for at most five subsequent trading sessions.
  - `FIRST_3_DAYS_WEEK_CLOSE`: the order is active Monday through Wednesday only; unresolved positions exit at that week's final close.
- A normal touch fills at the limit price. If the market opens below the limit, fill at the next week's open to model adverse gap execution.
- After a fill, set target to `fill × 1.05` and stop to `fill × 0.95`.
- Hold until target, stop, or the selected rule's time limit. In the five-trading-day variant, a later weekly setup is skipped while the previous position remains open and is recorded as `POSITION_OPEN_SKIP`.
- If a daily candle reaches both target and stop, use stop-first as the conservative daily-OHLC assumption and flag the result as ambiguous.
- Do not open another trade for the same stock while a position is active.
- Report every weekly setup: pre-market filter skip, no-fill, target hit, stop hit, and time exit, plus gross results. Costs are out of scope for V1.

## Confirmed Decisions

- The default UI selection is any-day entry with a maximum five-trading-day hold; the Monday–Wednesday/Friday-close rule remains available for comparison.
- A watchlist runs one independent position per stock and reports stock-level trades; portfolio capital allocation is out of scope for V1.
- VWAP and intraday candles are out of scope. V1 uses daily OHLC only.

## Hypothesis

Testing whether a retest of the immediately preceding week's low creates a repeatable short-term rebound. A prior-week low is not automatically institutional support; later versions should add context such as declining spread, volume/delivery absorption, or a prior markdown only after the price-only baseline is measured.

## Implementation Outcome — 2026-08-01

Implemented a dedicated Kotlin engine, API endpoint, and React page. The backtest supports a single NSE equity selected through the existing instrument search or an existing index/watchlist selected through the existing watchlist options. It fetches six months of daily candles plus a small lookback buffer, excludes the current incomplete week, supports both entry/holding variants, records `PREMARKET_FILTER_SKIP`, `OPEN_DEVIATION_SKIP`, `NO_FILL`, `POSITION_OPEN_SKIP`, `TARGET_HIT`, `STOP_LOSS`, and `TIME_EXIT`, and flags ambiguous stop/target candles. Focused engine tests, frontend interaction tests, frontend production build, and backend module compilation passed.
