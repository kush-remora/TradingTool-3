# Watchlist Weekly-Low Alignment Summary

The Three-Week Stock Review + Current Week watchlist page needs a separate evidence summary for stocks where any two adjacent displayed weeks have weekly lows within 1% of one another. This is a raw price-structure observation to help identify tightly aligned support; it is not a score, recommendation, or accumulation conclusion.

The page already derives its four displayed weekly summaries from daily candles, so the feature will remain frontend-only. A qualifying pair will use the absolute percentage difference relative to the earlier week's low (`abs(current - earlier) / earlier * 100 <= 1%`) and will show the stock, both week labels/lows, and the measured difference. Validation will cover boundary behavior and rendering for multiple matching pairs.

Implemented as a typed frontend utility plus a separate compact watchlist table. Focused tests (23) and the production build pass; `git diff --check` passes. The repository-wide strict TypeScript check still reports unrelated existing errors in other frontend files.

## Refined Requirement — 2026-08-10

The useful daily decision is narrower than every adjacent-week comparison: compare the latest observed (current, normally incomplete) week with the immediately preceding completed week, while keeping the preceding W-2 week visible for the original three-week context. A stock qualifies when the current-week low is within 1% of last week's low. This is a repeatable floor-watch cue, not a guaranteed buy, a claim of no downside, or an automatic Wyckoff conclusion.

The refined view will therefore show only the current-week alignment candidates and include the current-week, W-1, and W-2 low/date values plus two signed comparisons: current vs W-1 and W-1 vs W-2. Every date includes its weekday. If fewer than three observed week groups exist, the page should show an explicit empty state. Current-week maturity remains a manual filter. The existing per-stock weekly table remains the raw audit trail.

## Implementation Outcome — 2026-08-10

The watchlist summary now implements this floor-watch rule with a typed frontend calculation. It sorts daily candles, compares the latest three observed week groups, retains only current-week lows within 1%, and exposes Current Week, W-1, W-2, and both signed week-over-week comparisons. Focused tests pass, the frontend production build passes, and `git diff --check` passes. The repository-wide TypeScript check remains blocked by unrelated pre-existing errors elsewhere in `frontend/src`.

## Standalone UI Requirement — 2026-08-10

The alignment summary is important enough to become its own navigation page instead of remaining embedded below the broader watchlist review. The new screen will be a compact candidate table driven by the same watchlist scan endpoint and 1% rule. Each stock symbol will link to the existing **Three-Week Stock Review + Current Week** page with its symbol selected, so the summary remains the fast discovery screen and the existing review remains the evidence drill-down.

## Standalone UI Outcome — 2026-08-10

Added a dedicated **Weekly Low Alignment Summary** navigation page. It shows only qualifying floor candidates, keeps the last-week/current-week/latest-day low comparison visible, and links both the stock symbol and review button to the existing single-stock review. The duplicate embedded summary was removed from the broader watchlist page so there is one clear discovery entry point.

## Backtest Requirement — 2026-08-10

Add a separate six-month backtest for a selected watchlist. For each completed entry week, use the previous completed week's low (`L`) as the reference. A current-week retest qualifies only when its low is within 1% of `L` and the retest is at least five trading sessions after the date on which `L` occurred. A retest before that gap is reported as too soon, not silently treated as a trade. The entry price is fixed at `L × 1.01`; the target percentage and maximum holding period are user-configurable. There is no stop-loss. If the target is not reached, exit at the close after the configured maximum number of trading sessions (or the last available candle at the end of the dataset).

The backtest is deliberately separate from the existing Weekly Low Limit Backtest because its five-session alignment rule, target-only exit, and no-stop-loss behavior are different. It keeps one active position per stock at a time; overlapping later weekly setups are marked as position-open skips. The current incomplete week is excluded from the historical result.

## Backtest Implementation Outcome — 2026-08-10

Added a dedicated Kotlin engine/service and `/api/strategy/weekly-low-alignment-backtest/run` endpoint, plus a new navigation page. The page accepts a watchlist, target percentage, and maximum holding sessions, and shows all weekly setups with weekday-formatted dates, retest gap, entry/target, outcome, exit, holding period, and return. Focused Kotlin and frontend tests pass; the API/resource Maven build and frontend production build pass. Repository-wide TypeScript still reports unrelated pre-existing errors in other pages and test fixtures.
