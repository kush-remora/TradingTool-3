# Scalable Weekly Scanner Spec (Nifty 50 + Nifty 250 + Nifty Smallcap 250)

**Date:** 2026-04-17  
**Owner:** Kush  
**Skill Context:** Product Manager workflow

## Problem Statement
Current scanning is watchlist-centric and misses broader market opportunities. We need a weekly scanner that can evaluate a larger universe, filter weak candidates early, and prioritize setups with simple, explainable metrics.

## User Story
As a swing trader, I want a weekly scanner that:
1. filters stocks using existing fundamental data,
2. runs short-horizon historical validation (10-15 weeks) using a Monday-entry strategy,
3. flags how far current price is from the 10-week baseline low,
so I can quickly shortlist cleaner opportunities with better context.

## Scope (3 Parts)

### Part 1: Fundamental Filter on Expanded Universe
- Universe = union of `NIFTY_50`, `NIFTY_250`, `NIFTY_SMALLCAP_250`.
- Deduplicate overlapping symbols.
- Apply configurable fundamental filters using existing data only.
- Output: `fundamental_pass_list`.

### Part 2: 10-15 Week Monday Backtest
- Run backtest for each symbol in `fundamental_pass_list` for last `N` completed weeks (`N` in [10, 15], configurable).
- Entry rule: Monday buy (EOD-compatible v1 assumption).
- Exit rule: target %, stop-loss %, or max hold days.
- Output per stock:
  - trade count
  - win rate
  - average return
  - target hit count
  - stop-loss hit count
  - time-exit count

### Part 3: Baseline Distance Flag
- Compute baseline:
  - `baseline_low_10w = lowest low from last 10 completed weeks`
- Compute distance:
  - `distance_pct = ((current_price - baseline_low_10w) / baseline_low_10w) * 100`
- Add `distance_pct` to scanner output and ranking.

## Acceptance Criteria
1. Scanner supports all three universes and removes duplicates.
2. Fundamental filters are configurable (on/off + thresholds) without code changes.
3. Backtest lookback is configurable between 10 and 15 weeks.
4. Monday-entry + target/SL logic runs on every filtered stock.
5. Each stock gets a summary row with performance metrics.
6. Baseline low (10 weeks) and `distance_pct` are included in output.
7. Results are sortable by `distance_pct`, win rate, and average return.
8. Weekly run history is persisted for comparison.
9. Runtime remains practical for a single-user workflow (target: a few minutes, not hours).

## Data & Technical Considerations
- Backend: Kotlin + Dropwizard.
- DB: Supabase/PostgreSQL.
- Frontend: React + Ant Design.
- Market data: existing OHLC source (Kite / stored historical candles).
- Fundamentals: existing internal fields only in v1 (no new provider dependency).

### Suggested Configs (v1 defaults)
- `lookback_weeks`: 12
- `target_pct`: 6.0
- `stop_loss_pct`: 3.0
- `max_hold_days`: 10
- `baseline_weeks`: 10

## Out of Scope
- Intraday fills/slippage simulation.
- Portfolio sizing/allocation optimization.
- Auto-order placement from scanner output.
- ML prediction/ranking models.
- New fundamentals provider integration.

## Complexity Estimate
- Part 1: 2-3 days
- Part 2: 3-5 days
- Part 3: 2-3 days
- End-to-end v1: 7-11 working days

## Implementation Notes
- Keep v1 EOD-based for consistency and low complexity.
- Prioritize simple, explicit pipeline stages:
  1. Build universe snapshot
  2. Fundamental filter
  3. Backtest
  4. Baseline-distance metric
  5. Rank + persist + display
- Favor config-driven tuning over hardcoded constants.

