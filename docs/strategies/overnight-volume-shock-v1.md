# Overnight Volume Shock V1

## Summary

This strategy tests whether unusual daytime volume predicts a positive overnight move from the current close to the next session's open.

This is an overnight-only holding model:
- buy near the market close
- sell at the next market open

It should not be mixed with multi-day momentum or intraday continuation rules.

## Hypothesis

If a stock experiences an unusually large volume shock during the day, that shock may reflect attention, order imbalance, or information that has not been fully priced before the close. The remaining adjustment may appear overnight as a positive open-to-close gap.

## Signal Definition

Base formula:
- `volumeShock = todayVolume / averageDailyVolumeLastNSessions`

Two baseline lookbacks to compare:
- `20 sessions`
- `60 sessions`

Two portfolio-construction methods to compare:

Threshold-based:
- include stocks where `volumeShock >= 2.0`

Rank-based:
- rank all eligible stocks by `volumeShock`
- buy the top `20%` or top `N` names

The rank-based version is important because the research idea is more naturally framed as a cross-sectional ranking effect than a single hard threshold.

## Trade Rules

Entry:
- buy as close to market close as realistically tradable

Exit:
- sell exactly at the next market open

Hold period:
- one overnight session only

## Why This Must Stay Separate

This strategy is about overnight return capture, not next-day intraday continuation.

That distinction matters because recent research reports a positive relationship between volume shocks and subsequent overnight returns, while not finding the same effect in the next intraday session. Source: [Volume Shocks and Overnight Returns](https://papers.ssrn.com/sol3/papers.cfm?abstract_id=5156605)

## Universe and Filters

Recommended starting universe:
- liquid NSE cash equities only

Recommended filters:
- minimum 20-day average traded value
- exclude symbols with very large bid-ask friction if that data is available
- track earnings/event days separately if possible

## Reporting Requirements

Report at least:
- number of trades
- average overnight return
- win rate
- average gain
- average loss
- expectancy
- max drawdown across the full test
- return after estimated costs and slippage

Split results by:
- 20-day versus 60-day volume baseline
- threshold-based versus rank-based construction
- top `10%`, `20%`, `30%` ranked buckets
- gap-up versus gap-down frequency
- market regime

## Risks and Failure Modes

- Closing execution quality matters a lot.
- Overnight return may look good before costs but weaken after realistic close/open slippage.
- News-heavy names can dominate results and create unstable performance.
- The effect may be portfolio-level stronger than single-stock-level, so concentration rules matter.

## Out of Scope

- same-day intraday exits
- multi-day holding
- discretionary chart confirmation
- live broker execution

## Current Product Call

The first version should test both:
- a simple threshold model
- a rank-based model

The main question is not "does `2x volume` sometimes work?"

The real question is:

Does buying the strongest daily volume shocks at the close and selling at the next open produce a robust, tradable overnight edge after costs?
