# Absolute Delivery Threshold Strategy — Starting Hypothesis

The first research step is to observe NSE equity sessions with unusually large absolute participation: total traded quantity of at least `20,000,000` shares, delivery quantity above `5,000,000` shares, and delivery percentage above `60%`. These values are initial research thresholds, not yet proven evidence of institutional buying. A qualifying row should be treated as an **institutional-participation candidate**, because delivery data does not identify the buyer and can also appear during distribution or other transfers.

This first version is deliberately an observation scanner rather than a buy strategy. It should show every qualifying event and enough raw context to learn what happens next. Price breakout rules, Wyckoff structure, historical-relative volume, market-cap adjustment, entries, stops, targets, and ranking remain outside this starting scope until real output has been reviewed.

## Product Specification

### Problem Statement

Large absolute delivery activity is difficult to spot by reviewing daily NSE data manually. We need a simple six-month event audit that exposes sessions where both traded quantity and delivered quantity are exceptionally large, without hiding the raw evidence behind additional strategy logic.

### User Story

As a trader, I want to see stocks with very high traded quantity, delivery quantity, and delivery percentage so that I can study whether these events reveal meaningful institutional participation before adding price and Wyckoff confirmation rules.

### Absolute Delivery Gate

The absolute-delivery portion passes only when all three conditions are true on the same trading day:

1. `total_traded_quantity >= 20,000,000`
2. `delivery_quantity > 5,000,000`
3. `delivery_percentage > 60.0`

Boundary values are intentional: exactly 20 million traded shares passes; exactly 5 million delivered shares or exactly 60% delivery does not pass.

### Acceptance Criteria

- Scan the current active `groww_HIGH_QUALITY` institutional watchlist across the six calendar months ending on the latest stored delivery date.
- Include an event in the matched table only when all three delivery gates and all three mandatory uptrend gates pass.
- Display trading date, symbol, company, total traded quantity, delivery quantity, and delivery percentage.
- Display which thresholds passed using the raw values; do not label the event as accumulation, institutional buying, or a buy signal.
- Show separate matched-event and entire-watchlist symbol-day tables.
- Represent missing, incomplete, and absent delivery rows explicitly.
- Return an empty result cleanly when no stocks qualify.

### Technical Considerations

- Reuse `stock_delivery_daily`; the required fields already exist.
- Resolve the current active watchlist from `IndexConstituentKeys.GROWW_WATCHLIST`.
- This rule is different from the current Delivery Breakout scanner, which compares current volume and delivery quantity with the previous session using multipliers.
- Keep the thresholds fixed and return them in the API response so the UI cannot describe a different rule.
- For internally consistent NSE rows, traded quantity of at least 20 million and delivery above 60% already imply more than 12 million delivered shares. The 5-million floor is retained as an explicit audit rule, but it does not further narrow rows that pass the other two gates.

### Out of Scope

- Price-breakout detection
- Buy, sell, entry, stop-loss, or target rules
- Wyckoff accumulation/distribution classification
- Historical averages, Z-scores, rolling density, or market-cap-specific thresholds
- Price-performance backtesting or claims that every qualifying event represents institutional buying

### Complexity Estimate

Approximately one day to add a separate backend/API/UI path with focused tests and validation.

## Current Decision

Use the three-condition absolute threshold plus the three-condition established-uptrend validation as the baseline matched dataset. Review the resulting events before adding further price, structure, or historical-comparison layers.

## Institutional Watchlist Scanner Contract

Build this as a separate six-month event backtest over the existing **institutional watchlist grouping**. There is no CSV upload. For every current active symbol and every stored trading date in the six-month window, evaluate one symbol-day row independently.

The absolute-delivery gate passes only when all three conditions are true:

- `total_traded_quantity >= 20,000,000`
- `delivery_quantity > 5,000,000`
- `delivery_percentage > 60.0`

The final result also requires close above SMA50, SMA50 above SMA200, and SMA50 above its value 20 sessions earlier. The result returns two tables: qualifying symbol-day events and the complete symbol-day audit. V0 must not add relative-volume comparisons, entry/exit logic, or existing Delivery Breakout Validation rules. Missing records, `MISSING_FROM_SOURCE` rows, incomplete values, missing candles, and insufficient price history are shown explicitly and never qualify silently.

## Relationship to Existing Delivery Breakout Validation

The existing screen is a separate relative-shock scanner. For the latest available delivery date, it removes known ETFs, keeps rows with at least `10,000` traded shares and a delivery quantity, and compares each stock with its immediately previous available delivery row. The current runtime configuration requires both total traded quantity and delivery quantity to be at least `3x` their previous-session values.

Delivery percentage is displayed but does not affect qualification. There is no minimum absolute delivery quantity, no 60% delivery gate, no multi-day baseline, and no actual price-breakout condition. Price/LTP and daily percentage change are display context only. Results are ordered by delivery multiplier and then volume multiplier; the UI can further filter those returned results by volume multiplier, delivery multiplier, price change, and top-N count.

## Implementation Outcome

The standalone Absolute Delivery Backtest now exposes `GET /api/strategy/absolute-delivery/backtest`. It resolves the selected active grouping, uses the latest stored delivery date as the end date, evaluates the preceding six calendar months, and builds the full grouping-by-trading-date audit with event-date uptrend validation.

The frontend has a dedicated **Absolute Delivery Backtest** page with compact summary counts and two tabs: **Matched Events** and **Entire Grouping**. The full table exposes raw delivery and SMA evidence, all six gates, and missing-data states.

## Grouping Selection

The page should allow one active index grouping to be selected from the repository's current constituent catalogue. `groww_HIGH_QUALITY` remains the default, and changing the selection reruns the identical fixed formula and six-month audit for that grouping. This changes only the universe membership; thresholds, dates, event semantics, and output columns remain fixed.

## Mandatory Uptrend Validation

An Absolute Delivery event qualifies only when the event-date close is also in an established uptrend. Using stored daily candles available on or before that event date, all three conditions must pass: close above SMA50, SMA50 above SMA200, and current SMA50 above its value 20 trading sessions earlier. The calculation must use TA4J and exact trading-session indices without future candles.

Rows without a candle on the delivery date or without enough history for SMA200 and the 20-session SMA50 comparison remain visible in the full audit with an explicit trend-data status and never match. The API and UI should expose close, SMA50, SMA200, prior SMA50, the three individual trend gates, and the combined uptrend result.
