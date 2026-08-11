# Weekly low alignment backtest — 2026-08-10

## Why it was built

The weekly-low alignment summary identifies a possible floor retest, but it needed historical evidence before being used as a repeatable decision cue. This page runs the same idea across a selected watchlist for the previous six months.

## What was implemented

- Watchlist selection from the existing weekly-price-review universe endpoint.
- Configurable target percentage and maximum holding sessions.
- Clear audit table for W-1 low, retest date/low, trading-session gap, fixed entry, target, exit, and return.
- Explicit `No retest`, `Retest too soon`, `Target hit`, `Time exit`, and position-open outcomes.
- The UI states that the strategy has no stop-loss.

## Decisions and tradeoffs

- The backtest is a separate strategy/API flow because the five-session retest rule and target-only exit differ from the existing Weekly Low Limit Backtest.
- A retest must be within 1% of W-1 low and at least five trading sessions after the exact W-1 low date. Earlier touches remain visible as `Retest too soon`.
- Entry is fixed at W-1 low × 1.01. If the target is not reached, the position exits at the holding-period close. Only one position per stock is allowed; overlapping weekly setups are skipped.

## Validation

- Kotlin engine tests cover the DMART-style one-session retest, the five-session qualifying retest, configurable target behavior, and a negative time exit without a stop-loss.
- Focused frontend tests and the production build pass.
- API/resource compilation passes with Maven.

## Follow-up

Review the first real watchlist results manually before using the output for paper trading. The current incomplete week is excluded from the historical result.
