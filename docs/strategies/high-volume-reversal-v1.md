# High Volume Reversal V1

## Summary

This strategy tests whether large down days with unusually high volume create a short-term overreaction that later reverses.

The original research idea applies to both large up moves and large down moves, but this repo should start with the India cash-market-friendly version:
- long-only
- focused on sharp down days

## Hypothesis

When a stock falls sharply on abnormally high volume, the market may have overreacted to the news or event. If that overreaction is excessive, some of the move may reverse over the next several trading sessions.

## Large-Move Definition

Use a hybrid rule instead of only a fixed percentage or only a volatility-relative threshold.

Definitions:
- `dailyReturn = (close / previousClose) - 1`
- `volatility20 = standardDeviation(dailyReturn, last 20 sessions)`
- event day qualifies when:
  - `dailyReturn <= -max(5%, 2 * volatility20)`

Why this rule:
- the fixed `5%` floor keeps the event meaningful
- the `2 sigma` component keeps the move large relative to the stock's own history

## High-Volume Definition

Primary rule:
- `dailyVolumeShock = todayVolume / averageDailyVolumeLast20Sessions`
- high-volume event when `dailyVolumeShock >= 2.0`

Optional variant to compare:
- `1.5x`
- `2.0x`
- `3.0x`

## Trade Rules

India-compatible V1:
- buy after a large down day with high abnormal volume

Entry variants to compare:
- buy at event-day close
- buy at next-day open

Hold periods to test:
- `2 trading days`
- `5 trading days`
- `20 trading days`

Exit handling:
- base study can use pure hold-period exits
- optional second pass can add stop-loss rules to see whether the edge survives with practical risk control

## Why Long-Only First

In Indian cash equity, holding short stock positions overnight is not the default path for a simple retail workflow. That makes the "short large up day" side operationally messy for this repo.

Starting with long-only crash reversals keeps the study simple, tradable, and easier to compare with actual execution later.

## Universe and Filters

Recommended starting universe:
- liquid NSE cash equities only

Recommended exclusions:
- stocks in or near lower circuit
- very low-price names
- symbols with weak historical liquidity

Recommended tagging:
- event with earnings/news if detectable
- gap-down event versus intraday selloff event

## Reporting Requirements

Report at least:
- number of events
- average forward return for 2, 5, and 20 days
- win rate by hold window
- max drawdown during hold
- return after costs

Split results by:
- entry at close versus next open
- `1.5x`, `2.0x`, `3.0x` volume shock
- event size bucket
  - `5% to 7%`
  - `7% to 10%`
  - `>10%`
- market regime
  - strong market
  - weak market
  - sideways market

## Risks and Failure Modes

- Some large down moves are justified repricings, not overreactions.
- Lower-circuit names can look statistically attractive but be practically untradable.
- The stronger the event, the more news risk and overnight gap risk matter.
- A clean academic reversal effect may disappear after slippage and selection filters.

## Out of Scope

- overnight short selling in cash equity
- options or futures implementation
- automatic news interpretation
- live execution logic

## Current Product Call

This should be treated as a clean event-study backtest, not a live scanner first.

The first version should answer:
- do large down days with high volume actually bounce in this market and this universe?
- does entry at close or next-day open work better?
- is the edge still real after costs and realistic tradability filters?
