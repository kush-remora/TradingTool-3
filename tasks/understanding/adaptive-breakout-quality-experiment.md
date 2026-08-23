# Adaptive Breakout Quality Experiment

## Objective

Test two candidate quality controls on the core watchlist without assuming that either one improves trading outcomes:

1. **Base quality:** prefer a sufficiently established and compact pre-breakout base.
2. **Breakout-day move:** prefer a close-to-close breakout-day move below 3%; treat 3–6% as a middle bucket and above 6% as a high-risk bucket.

The current backtest remains the baseline: each selected fresh breakout enters at the first later tradable open, uses the existing target/stop, gap guard, costs, slippage, and one-position lock. Only a measure with evidence of predictive value may become a quality filter. Breakout-day move remains a diagnostic bucket, not a gate.

## Initial definitions

### Base quality

Use only candles available at the breakout close. The engine's candidate-floor date is a recent swing anchor, not necessarily the start of the congestion. Therefore search the last 120 pre-breakout sessions for the longest contiguous trailing range within the width bands.

- Duration: sessions in the selected trailing congestion range through the pre-breakout session.
- Width: `(highest high - lowest low) / lowest low` across that base.
- `PASS`: at least 40 sessions and width at most 20%.
- `WAIT`: at least 20 sessions and width at most 30%.
- `REJECT`: otherwise.

These are initial experiment buckets, not permanent trading doctrine. The backtest exposes the raw duration and width so the thresholds can be challenged.

### Breakout-day move

Use close-to-close percentage change from the previous completed session's close to the breakout close.

- `PASS`: move `< 3%`.
- `WAIT`: move `>= 3%` and `<= 6%`.
- `REJECT`: move `> 6%`.

This is intentionally separate from the next-session entry gap. A stock can have a modest breakout-day move and still gap too far for entry, or have a large breakout-day move and open near the breakout close.

## Success measurements

Report all of these for the baseline and every filter run:

- target hit before stop under the current ±5% trade model;
- net expectancy, average win/loss, payoff ratio, and profit factor;
- close-to-close continuation after 5, 10, and 20 sessions from entry;
- whether the +5% target was reachable within 5, 10, and 20 sessions;
- sample size and skipped-entry-gap count.

No single horizon is declared the answer in advance. The 5-session result measures immediate follow-through, 10 sessions measures a normal short swing, and 20 sessions measures whether the breakout created a sustained move rather than a one-day reaction.

## Constraints

- No delivery percentage quality rule.
- No next-day data in the quality decision or filter.
- No combined opaque score; individual control results must remain visible.
- No threshold tuning from one stock or one result set.
- Core-watchlist results retain the current constituent list and are subject to survivorship bias.

## Planned validation

Run three comparable cases over the same six-month core-watchlist window:

1. `NONE` baseline.
2. `BASE_QUALITY` only.
3. `BASE_QUALITY` only.

The breakout-day move buckets are still reported inside the baseline run for research, but are not selectable as a quality gate.

If the sample is too small, report the result as inconclusive rather than declaring success from a higher percentage alone.

## 2026-08-21 result

The six-month window was 2026-02-21 through 2026-08-21 across the eight-stock `core` watchlist. The baseline produced 48 signals, 40 entered trades, 6 gap skips, 23 targets, 17 stops, 57.5% target-before-stop win rate, 0.33% expectancy, and 1.14 profit factor.

The first implementation incorrectly used the causal candidate-floor date as the base start; it produced no PASS bases because that engine field is a recent swing anchor. That was corrected before treating the result as evidence. With the trailing-range search, base quality produced 4 PASS signals: all 4 entered and all 4 hit target, but the sample is too small to call this validated. The base buckets in the unfiltered replay were PASS 4/4 wins (100%), WAIT 13/20 (65%), and REJECT 6/16 (37.5%); this is promising but highly sensitive to a four-trade sample.

The breakout-day move control did not improve this sample. Its strict PASS bucket had 4/8 wins (50%), WAIT had 10/17 (58.8%), and REJECT (>6%) had 9/15 (60%). Filtering to PASS only reduced the replay to 9 entries, 4 wins, 5 stops, -1.28% expectancy, and 0.61 profit factor. The combined strict filter left one trade, so it is inconclusive.

Forward continuation is reported at 5, 10, and 20 holding sessions. For the baseline, positive close rates were 55.0%, 60.5%, and 61.1%; +5% was reachable in 57.5%, 65.8%, and 77.8% of observations. These are diagnostic horizons, not new exit rules.
