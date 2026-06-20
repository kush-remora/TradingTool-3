# Requirement: Daily Volume Router

## Problem Statement

A raw daily list of top-volume movers is too noisy to use directly. It mixes FOMO blow-offs, genuine institutional breakouts, and steady continuation names in one list. If we keep the list raw, it becomes another attention sink instead of a decision tool.

## Goal

Create a daily routing workflow that ingests a top-volume mover feed, enriches each stock with shared market metrics, ranks the list down to a small relevant shortlist, and classifies each shortlisted stock into one actionable bucket.

## User Story

As a trader, I want the daily high-volume list reduced to a small set of ranked names with a clear bucket label, so I can quickly tell whether a stock is a short-fade setup, a trend continuation, a Wyckoff breakout candidate, or just noise.

## Product Decision

- The system may ingest up to 100 raw mover rows for the day, but the canonical stored shortlist should contain at most 20 ranked candidates.
- The router is a separate workflow from the Wyckoff footprint scanner.
- The router depends on the shared market-metrics contract and may optionally consume Wyckoff footprint state as one input.
- The actionable buckets should be presented in separate views, not one blended table.

## Required Inputs

For each candidate, the enriched payload must support:
- `symbol`
- `trade_date`
- `snapshot_price_change_pct` or `close_change_pct` as appropriate
- `routing_price_change_pct`
- `today_volume`
- `today_high`
- `delivery_quantity`
- `delivery_pct`
- `volume_base_avg`
- `delivery_qty_base_avg`
- `volume_shock_ratio`
- `delivery_shock_ratio`
- `highest_high_30d`

## Ranking Rules

1. Reduce the raw feed to a ranked shortlist of at most 20 names.
2. Prefer NSE-tradable names for the primary shortlist.
3. De-prioritize obviously illiquid names even if their raw percentage move looks large.
4. Prefer names where price move and shock intensity are both meaningful, not just one of the two.
5. Watchlist overlap or existing Wyckoff footprint state may be used as a ranking boost, but not as the only reason to keep a stock.

## Classification Matrix

### Bucket 1: `SHORT_CANDIDATE`

Purpose:
- identify retail FOMO or churn-heavy blow-off moves suitable for mean-reversion or intraday short review.

Conditions:
- `routing_price_change_pct > 10.0`
- `volume_shock_ratio > 15.0`
- `delivery_shock_ratio < 5.0`

### Bucket 2: `WYCKOFF_BREAKOUT`

Purpose:
- identify structural breakout behavior where price, volume, and delivery all confirm institutional markup.

Conditions:
- `routing_price_change_pct >= 5.0` and `<= 10.0`
- `volume_shock_ratio >= 5.0` and `<= 15.0`
- `delivery_shock_ratio >= 5.0`
- `today_high > highest_high_30d`

### Bucket 3: `TREND_LONG`

Purpose:
- identify controlled continuation names that may keep moving because accumulation is present without blow-off conditions.

Conditions:
- `routing_price_change_pct >= 2.0` and `<= 6.0`
- `volume_shock_ratio >= 2.0` and `<= 5.0`
- `delivery_shock_ratio >= 3.0`

### Bucket 4: `DISCARD`

Purpose:
- remove names that do not match any of the three actionable behaviors.

## Acceptance Criteria

1. Each shortlisted stock must end with exactly one action bucket.
2. The router must store `trade_date` on every routed row.
3. The stored shortlist must keep only the fields needed for later review, routing explanation, and follow-up scanners.
4. The router must not persist all 100 raw candidates as the main stored review table.
5. The final output should preserve the reason for classification:
   - price band hit
   - volume shock band hit
   - delivery shock band hit
   - breakout-high check where applicable
6. The UI requirement for this workflow must support separate views for:
   - `SHORT_CANDIDATE`
   - `TREND_LONG`
   - `WYCKOFF_BREAKOUT`

## View Structure

- `Short View`: mean-reversion / intraday short review candidates only
- `Trend Long View`: controlled continuation candidates only
- `Remora Breakout View`: Wyckoff breakout candidates only

`DISCARD` names should stay out of the actionable views.

## Technical Considerations

- If the source feed only provides snapshot price data, the output must label it clearly as snapshot math.
- `routing_price_change_pct` should be a resolved router field derived from the correct shared source:
  - `snapshot_price_change_pct` for intraday or copied snapshot runs,
  - `close_change_pct` for EOD runs.
- The `today_high > highest_high_30d` rule requires enriched OHLC history, not just a copied top-movers payload.
- This workflow is a daily triage engine, not a final execution engine.

## Out of Scope

- Broker order placement.
- Position sizing.
- Stop-loss and target rules for each bucket.
- Full historical backtest logic for the router.

## Complexity Estimate

2-4 days for a clean first implementation once the shared metrics layer is settled.
