# NETWEB cycle tracker

## Current understanding

NETWEB is being treated as a stock-specific cycle rather than as one row in a generalized scanner. The supplied history shows a repeating sequence: a relatively narrow weekly rotation/base, an expansion or bull run, a drawdown/correction, and then a new base at a different price level. The tracker must preserve both opportunities: smaller rotations of roughly 5% inside the weekly phase and larger multi-day expansion runs of roughly 15–20% or more.

The daily screen should state the current phase and show the evidence behind it. The first version should use explicit, explainable state transitions and adaptive ranges derived from NETWEB’s own recent history. It must distinguish a normal rotation from a genuine expansion and from a drawdown/reset; it should not force a signal when the evidence is mixed. The supplied CSV contains 186 trading records from 2025-11-07 through 2026-08-07, which is enough to define a v1 replay model but not enough to treat the behavior as a permanent invariant.

## Proposed daily states

- **Weekly rotation/base:** price remains contained in a recent range and produces repeated smaller swings. Show lower zone, upper zone, range width, swing count, and current position within the range.
- **Bull run/expansion:** price has accepted above the recent base and momentum is continuing. Show breakout date, gain from base, days in run, pullback from the run high, and volume/delivery evidence where available.
- **Drawdown/reset:** price is declining from an expansion high and has not yet formed a stable new base. Show peak, drawdown percentage, support from the prior base, and whether selling pressure is easing.
- **New-base formation:** optional internal sub-state of drawdown until the range stabilizes; it becomes the next weekly rotation only after the evidence is sufficient.

## Daily output expectation

The screen should answer: “What phase is NETWEB in today, why do we believe that, what changed since yesterday, and what should I watch next?” It should include a confidence/evidence score, phase age, recent phase timeline, active range, invalidation level, and a clear candidate action such as monitor the lower rotation zone, monitor the upper rotation zone, hold/watch the bull run, or wait for a new base.

## Decisions still to validate in replay

Exact range width, minimum phase duration, breakout buffer, drawdown threshold, and the definition of a completed 5% swing must be calibrated against the supplied history. The scanner should be replay-tested before being used for live decisions.

## Classification approach

The phase engine must not classify a move from its percentage alone. It will first maintain an active NETWEB base using recent closes and the recent high/low structure, then evaluate each new daily candle against that base and the latest expansion peak.

- A 5% move that remains inside the active base is a **weekly rotation**.
- A move that closes above the base high with follow-through and supporting expansion evidence is a **bull run**, even if the individual day is only 5%.
- A move that falls materially from the expansion peak and loses short-term support, before a stable range forms, is a **drawdown**.
- A range that remains contained and becomes quieter for enough sessions is a **new base**, which can become the next weekly phase.

The implementation should be a deterministic, replayable state machine with phase hysteresis: a single noisy candle should not flip the label. Each daily result must retain the inputs used for the decision, including base boundaries, position within the range, short-term returns, distance from the expansion high, range width, follow-through count, and available volume/delivery evidence. This makes the label explainable and lets us calibrate thresholds against the supplied history.

## First implementation outcome

The first reviewable slice is implemented in `core/.../strategy/netwebcycle`, exposed through `POST /api/strategy/netweb-cycle/run`, and shown in the `NETWEB Cycle Tracker` frontend page. The engine replays the supplied CSV behavior as repeated rotation, bull-run, and drawdown segments; the latest supplied date, 2026-08-07, is classified as a bull run after the July base breakout. The current slice uses price and volume evidence only; delivery evidence and trade-result backtesting remain follow-ups for review.
