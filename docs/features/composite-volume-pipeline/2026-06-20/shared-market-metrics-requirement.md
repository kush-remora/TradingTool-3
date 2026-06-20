# Requirement: Shared Market Metrics Contract

## Problem Statement

The current strategy direction mixes multiple meanings of "daily percentage," volume baselines, and delivery baselines across different notes. That makes it easy for the Wyckoff scanner, daily mover router, and future dashboards to talk about the same stock using different math.

## Goal

Create one shared calculation contract for daily price movement, buffered baselines, volume shock, delivery shock, and breakout reference levels so every downstream strategy uses the same definitions.

## User Story

As a trader, I want every strategy screen to calculate price move and shock metrics the same way so I can compare results across workflows without second-guessing what each percentage means.

## Acceptance Criteria

1. `trade_date` is mandatory on every saved run, snapshot, and routed shortlist row.
2. Percent fields must be explicit about their denominator and time context:
   - `snapshot_price_change_pct`: snapshot price vs previous close for copied external top-mover feeds.
   - `close_change_pct`: official close vs previous close for EOD workflows.
   - `distance_to_200sma_pct`: close vs 200 SMA.
3. The system must not use a generic `daily_pct` field where the denominator is ambiguous.
4. Shared buffered-baseline fields must exist for both volume and delivery:
   - `recent_buffer_days_excluded`
   - `volume_base_avg`
   - `delivery_qty_base_avg`
5. Shared shock ratios must use those buffered baselines:
   - `volume_shock_ratio = today_volume / volume_base_avg`
   - `delivery_shock_ratio = today_delivery_qty / delivery_qty_base_avg`
6. Shared breakout-context fields must exist where the workflow needs them:
   - `highest_high_30d`
   - `daily_volatility_pct`
   - `spread_squeeze_pct`
7. All upstream historical OHLC and delivery inputs used for these calculations must be split-adjusted and bonus-adjusted before entering the scanner pipeline.

## Technical Considerations

- Copied external feeds like Groww may only provide `ltp` and prior close at capture time. That should be stored as snapshot math, not silently reused as final EOD close math.
- Baseline windows must be configurable, not hardcoded into each strategy.
- Buffered baselines should exclude the most recent N sessions so active accumulation or breakout behavior does not poison its own denominator.
- Shared metric names should be reused in API payloads, stored models, and table columns where possible.

## Out of Scope

- Full UI design for every downstream screen.
- Strategy-specific ranking formulas.
- Intraday bar math beyond fields required by the current router requirement.

## Complexity Estimate

1-2 days for naming decisions, data-contract updates, and downstream migration planning before code changes begin.
