# CSV Breakout Quality Checklist — Proposal

## Objective

Improve the quality of CSV breakout entries without assuming that any one indicator guarantees success. The first version should be an offline checklist and a backtestable filter set.

## Core hypothesis

A 52-week breakout is higher quality when price is accepted above the prior high, short-term momentum is positive but not excessive, and participation is visible through volume. Delivery data is supporting evidence, not an initial hard rejection rule.

## V1 required checks

1. **52-week breakout:** signal-day high exceeds the previous 252-session high.
2. **Strong close:** signal-day Close Location Value is at least 0.70: `(close - low) / (high - low) >= 0.70`. This means the close is in the top 30% of the candle's range. Reject zero-range candles.
3. **Momentum:** 20-session ROC is positive.
4. **Trend:** price is above a rising 50-day SMA.
5. **Participation:** signal-day volume is at least 1.5 times its 20-session average.
6. **No chase:** signal-day close-to-close gain does not exceed the existing configurable limit (currently 6%).
7. **Confirmation:** the next trading day closes at or above the breakout level. Entry can then use the existing Two Green Candles flow.

## Delivery score (not a hard rule yet)

- Delivery percentage at or above its 20-day median: +1.
- Delivery percentage at or above its 20-day 75th percentile: +2.
- Treat the score as a review signal until backtesting shows that it improves expectancy on NSE data.

## Chartink / TradingView checklist

- Add ROC(5) and ROC(20), both above zero. If ROC is unavailable in Chartink, use `latest close > 5 days ago close` and `latest close > 20 days ago close` as the equivalent direction checks.
- Add SMA(50) and SMA(200): close above both, and latest SMA(50) above SMA(50) ten days ago.
- Require latest volume >= `1.5 × SMA(volume, 20)`.
- Require latest high > the previous 252-session high for a genuine 52-week breakout.
- Add the Close Location Value rule above; it can be expressed directly using Chartink arithmetic or saved as a custom indicator.
- Confirm the next-day hold above the breakout level.
- Check delivery data separately in the TradingTool/NSE data view.

## Backtest discipline

Test every proposed filter separately and then in combination. Compare trade count, win rate, average return, expectancy, profit factor, and maximum drawdown. Do not accept a filter solely because it increases win rate on a very small sample.

## Not included in V1

- Predicting institutional buying from delivery percentage alone.
- Optimizing thresholds from a single stock or one historical period.
- Changing the existing entry and exit rules before the checklist is validated.
