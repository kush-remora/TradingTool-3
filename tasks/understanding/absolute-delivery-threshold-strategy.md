# Absolute Delivery Threshold Strategy — Starting Hypothesis

The first research step is to observe NSE equity sessions with unusually large absolute participation: total traded quantity of at least `20,000,000` shares, delivery quantity above `10,000,000` shares, and delivery percentage above `60%`. These values are initial research thresholds, not yet proven evidence of institutional buying. A qualifying row should be treated as an **institutional-participation candidate**, because delivery data does not identify the buyer and can also appear during distribution or other transfers.

This first version is deliberately an observation scanner rather than a buy strategy. It should show every qualifying event and enough raw context to learn what happens next. Price breakout rules, Wyckoff structure, historical-relative volume, market-cap adjustment, entries, stops, targets, and ranking remain outside this starting scope until real output has been reviewed.

## Product Specification

### Problem Statement

Large absolute delivery activity is difficult to spot by reviewing daily NSE data manually. We need a simple six-month event audit that exposes sessions where both traded quantity and delivered quantity are exceptionally large, without hiding the raw evidence behind additional strategy logic.

### User Story

As a trader, I want to see stocks with very high traded quantity, delivery quantity, and delivery percentage so that I can study whether these events reveal meaningful institutional participation before adding price and Wyckoff confirmation rules.

### V0 Signal Definition

A stock qualifies only when all three conditions are true on the same trading day:

1. `total_traded_quantity >= 20,000,000`
2. `delivery_quantity > 10,000,000`
3. `delivery_percentage > 60.0`

Boundary values are intentional: exactly 20 million traded shares passes; exactly 10 million delivered shares or exactly 60% delivery does not pass.

### Acceptance Criteria

- Scan the current active `groww_HIGH_QUALITY` institutional watchlist across the six calendar months ending on the latest stored delivery date.
- Include a row only when all three V0 conditions pass.
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
- At exactly 20 million traded shares, a delivery percentage above 60% already implies more than 12 million delivered shares. The 10-million delivery floor still matters for higher-volume sessions where the percentage and absolute-quantity gates may diverge.

### Out of Scope

- Price-breakout detection
- Buy, sell, entry, stop-loss, or target rules
- Wyckoff accumulation/distribution classification
- Historical averages, Z-scores, rolling density, or market-cap-specific thresholds
- Price-performance backtesting or claims that every qualifying event represents institutional buying

### Complexity Estimate

Approximately one day to add a separate backend/API/UI path with focused tests and validation.

## Current Decision

Use the three-condition absolute threshold as the baseline dataset. Review the resulting events first; only then decide which price, structure, and historical-comparison layers improve signal quality.

## Institutional Watchlist Scanner Contract

Build this as a separate six-month event backtest over the existing **institutional watchlist grouping**. There is no CSV upload. For every current active symbol and every stored trading date in the six-month window, evaluate one symbol-day row independently.

A row passes only when all three conditions are true:

- `total_traded_quantity >= 20,000,000`
- `delivery_quantity > 10,000,000`
- `delivery_percentage > 60.0`

The result returns two tables: qualifying symbol-day events and the complete symbol-day audit. V0 must not add relative-volume comparisons, price conditions, entry/exit logic, or existing Delivery Breakout Validation rules. Missing records, `MISSING_FROM_SOURCE` rows, and incomplete values are shown explicitly and never qualify silently.

## Relationship to Existing Delivery Breakout Validation

The existing screen is a separate relative-shock scanner. For the latest available delivery date, it removes known ETFs, keeps rows with at least `10,000` traded shares and a delivery quantity, and compares each stock with its immediately previous available delivery row. The current runtime configuration requires both total traded quantity and delivery quantity to be at least `3x` their previous-session values.

Delivery percentage is displayed but does not affect qualification. There is no minimum absolute delivery quantity, no 60% delivery gate, no multi-day baseline, and no actual price-breakout condition. Price/LTP and daily percentage change are display context only. Results are ordered by delivery multiplier and then volume multiplier; the UI can further filter those returned results by volume multiplier, delivery multiplier, price change, and top-N count.

## Implementation Outcome

The standalone Absolute Delivery Backtest now exposes `GET /api/strategy/absolute-delivery/backtest`. It resolves the current `groww_HIGH_QUALITY` membership, uses the latest stored delivery date as the end date, evaluates the preceding six calendar months, and builds the full watchlist-by-trading-date audit without adding price logic.

The frontend has a dedicated **Absolute Delivery Backtest** page with compact summary counts and two tabs: **Matched Events** and **Entire Watchlist**. Focused Kotlin analyzer tests and React page tests cover threshold boundaries, missing-data states, cross-product counts, ordering, both table views, and the empty-match state.
