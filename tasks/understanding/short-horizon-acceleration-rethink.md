# Short-Horizon Acceleration Rethink

## Problem statement

The current Accelerating state compares the latest 5-day close-to-close move with the previous 5-day move. A stock is labelled accelerating when the latest period is positive and improves by at least one percentage point. This is useful as a pace comparison, but it does not distinguish a fresh move from a continuation after a large run.

HFCL demonstrates the issue: on 13 Aug its Now 5D move was about +11.6% versus Prior 5D at +10.3%, so the pace delta was +1.35%. The current 20D net move was only about +1.3% because the earlier 10D move was about -17.7%, while the latest close was already about +23% above the lowest recent 20-session close. The net-return extension warning therefore misses the recent run-up.

## Proposed solution

Keep the current pace calculation as raw evidence, but add a separate stage classification:

- Fresh acceleration: positive Now 5D, pace improvement of at least 1 percentage point, the Prior 5D move is not already strong, and the close has not travelled beyond the recent 20-session extension limit.
- Recovery: the latest period improves after a negative prior period; this is not automatically treated as a fresh breakout.
- Continuation: both periods are positive, but the pace improvement is small or the prior period was already strong.
- Extended continuation: pace may still be improving, but the recent move from the 20-session low or prior 5D move is already too large for a new entry.
- Steady and Weakening: retain their current meanings.

The initial values for review are Prior 5D <= +5% for fresh acceleration and no more than +25% from the lowest close in the latest 20 sessions. These are candidate thresholds, not final strategy constants; they should be checked against historical examples before implementation.

## Tab behavior

- All Stocks shows the raw pace, stage classification, and extension context.
- Shortlist defaults to Fresh acceleration; its Any pace option can still expose other stages for research.
- Best aligned requires Fresh acceleration plus its existing historical-speed and strong-finish rules.
- Latest 2-day finish remains a strict subset of Best aligned and adds the latest-two-candle close-position condition.
- Fresh today remains a view of Latest 2-day finish entries whose first-seen date is the current completed session.
- Extended and recovery names remain visible in All Stocks and exploratory filters, but do not enter the high-conviction final path by default.

## Main use cases

| Case | Expected classification | Final-path behavior |
|---|---|---|
| Quiet base starts moving | Fresh acceleration | Eligible if existing evidence also passes |
| Prior period already +10%, latest period +11% | Continuation/extended | Do not call fresh acceleration |
| Negative prior period followed by positive move | Recovery | Observe; require extra confirmation |
| Large recent move from a 20-session low | Extended continuation | Keep visible, exclude from default final path |
| Latest two candles close strongly after fresh setup | Fresh acceleration plus final finish | Eligible for Latest 2-day finish |
| One-day spike with weak close/volume context | Not fresh or insufficient evidence | Do not promote |
| Healthy but flat continuation | Steady/continuation | Keep for research, do not force a trade |
| Pullback followed by a controlled re-acceleration | Fresh acceleration only if not extended | Eligible when evidence supports it |

## Validation before implementation

Backtest the proposed classification over historical scan dates and review:

- early breakouts that should remain visible;
- stocks that ran sharply before appearing;
- recoveries from deep declines;
- one-day spikes and failed breakouts;
- names that remain strong but are no longer fresh.

Acceptance should include HFCL: it may remain visible as an extended/recovery context case, but it must not enter the fresh high-conviction path merely because Now 5D - Prior 5D is greater than one percentage point.

## Out of scope

- No buy/sell signal.
- No automatic GTT target generation.
- No removal of extended names from All Stocks.
- No threshold change until historical validation is reviewed.

## Refined classification proposal

Use completed daily candles only and calculate:

- Now 5D = latest close versus the close five completed sessions earlier.
- Prior 5D = the close five sessions earlier versus the close ten sessions earlier.
- Pace change = Now 5D minus Prior 5D.
- Recent run-up = latest close versus the lowest closing price in the latest 20 completed sessions.
- 20D net move remains visible context, but it is not the extension gate because an earlier decline can cancel a recent run-up.

Classify pace in this order:

1. Accelerating: Now 5D > 0, Prior 5D > 0, and Pace change >= 1 percentage point.
2. Rising, recovering: Prior 5D <= 0 and Pace change > 0, including a positive move after a negative prior period.
3. Rising, steady: Now 5D > 0, Prior 5D > 0, and Pace change is between -1 and +1 percentage points.
4. Weak: Pace change <= -1 percentage point, or Now 5D <= 0 without improving pace.
5. Unknown: insufficient history.

Classify stage independently using initial review thresholds:

- Fresh: Recent run-up <= 15% and Prior 5D < 5%.
- Review zone: not Fresh, but Recent run-up <= 25% and Prior 5D <= 10%.
- Extended: Recent run-up > 25% or Prior 5D > 10%.
- Unknown: insufficient 20-session history.

The combined label is the pace label plus the stage label, for example Accelerating · Fresh, Rising, recovering · Review zone, or Accelerating · Extended. The final path accepts Accelerating or Rising states only when the stage is Fresh or Review zone. It blocks every Extended stage and every Weak state. Existing historical-speed, strong-finish, recent-proof, and latest-two-candle conditions remain additional gates.

With these rules, HFCL on 13 Aug is Accelerating · Extended because its Prior 5D move was already above 10%, even though the pace improved by 1.35 percentage points. On 11 Aug its pace was weakening because Now 5D was below Prior 5D, so the strong latest-two candle condition alone would not promote it into the final path.
