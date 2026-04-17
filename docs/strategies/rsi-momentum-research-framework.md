# RSI Momentum Research Framework

## Why this document

This is the working agreement for RSI momentum backtesting so we stay explainable and avoid overfitting.

We are not looking for a single lucky backtest. We want repeatable behavior that can be explained trade-by-trade.

## Current system reality

Most of the building blocks already exist in the codebase:

- Multi-profile RSI momentum snapshots and history
- Stateful rank-based backtest
- Sniper target/stop backtest
- Safe-rule filters (rank filter, move from low filter, max daily move filter, blocked entry days)
- Rank improvement (`rank5DaysAgo`, `rankImprovement`) available in snapshots

This framework organizes that existing capability into clear experiment lanes.

## Non-negotiable principle: explainability

Every entry and exit must be explainable on screen and in backtest output.

For each trade decision, capture:

- `action`: `ENTRY`, `EXIT`, `HOLD`, `SKIP`
- `lane`: which strategy lane produced the decision
- `reasonCode`: short machine-readable code
- `reasonText`: one-line human explanation
- `ruleSnapshot`: key values used by that decision (rank today, rank 10d ago, jump score, weekday, target, stop, etc.)

Example reason text:

- `ENTRY`: "Entered because rank improved from 15 to 2 in 10 days and safety checks passed."
- `EXIT`: "Exited because rank dropped below give-up threshold."
- `EXIT`: "Exited because 10% target was hit."

## Experiment lanes

### Lane A: Classic RSI Momentum

- Entry: rank-based eligibility and safety filters
- Exit: rank weakness / give-up rank rules
- No fixed profit target

### Lane B: RSI Momentum + Fixed Target

- Same entry as Lane A
- Exit includes target (for example 10% or 15%) and stop logic

### Lane C: Rank-Jump Momentum

- Focus on acceleration, not just static top rank
- Core signal:
  - `jumpScore = rank_lookback_days_ago - rank_today`
- Example: rank 15 to rank 2 gives jump score `+13`
- Entry requires both:
  - current rank inside entry band
  - minimum jump score threshold

## Weekday buy policy experiments

Treat weekday beliefs as testable policy, not fixed truth.

Allowed policies:

- `ANY_DAY`
- `NO_FRIDAY`
- `NO_THU_FRI`

## Minimal experiment matrix (first pass)

Keep matrix small to avoid curve fitting.

1. Lane A + `ANY_DAY`
2. Lane A + `NO_FRIDAY`
3. Lane A + `NO_THU_FRI`
4. Lane B (10% target) + `ANY_DAY`
5. Lane B (10% target) + `NO_FRIDAY`
6. Lane B (15% target) + `NO_FRIDAY`
7. Lane C (jump) + `ANY_DAY`
8. Lane C (jump) + `NO_FRIDAY`
9. Lane C (jump + 10% target) + `NO_FRIDAY`

## Overfitting control rules

- Change only 1-2 knobs per experiment.
- Keep `lookbackDays` and jump threshold set from a small approved set.
- Use walk-forward splits (older train period, newer test period).
- Promote a variant only if it wins consistently across multiple test windows.
- No ad-hoc one-off tuning outside the experiment matrix.

## Temporary scope decision

As agreed for now:

- Skip transaction/slippage modeling in this phase.
- Keep a clear placeholder in experiment output so it can be added later without redesign.

## Output and organization

Each experiment run should produce:

- config used
- summary metrics
- trade list
- reason-code breakdown
- small sample of human-readable decision explanations

Recommended folder pattern:

- `build/reports/rsi-momentum-experiments/<experiment-id>/<run-date>/`

## Practical guidance

- Use this document as the current operating rulebook for RSI momentum research.
- Keep `rsi-momentum-v1.md` as implementation history.
- Keep this file focused on experiment discipline and decision explainability.
