# Understanding: Momentum Strategy Sequencing on the Watchlist

The momentum work will be implemented one strategy at a time. Each strategy must run against the maintained Groww/NSE watchlist, remain explainable, and produce a research view for manual review rather than place orders or automatically manage a portfolio. Wyckoff remains the shared market worldview, but the momentum strategies are separate short-term continuation tools with their own holding-period and validation rules.

The initial Classic Relative Momentum proposal was rejected as the first implementation because endpoint returns can hide the recent path: a stock may show a strong three-month return while weakening during the latest month, carrying excessive volatility, or already moving down. The first implementation should instead be Multi-Timeframe RSI Momentum on the watchlist. It should use the RSI Highway as a trend-health gate, while exposing recent direction and volatility as visible supporting fields. The 52-week-high, risk-adjusted, and WIS-style portfolio rules will be added later as separate experiments or overlays.

## Decision

- First strategy: Multi-Timeframe RSI Momentum (RSI Highway).
- First scope: watchlist scan only; no auto-buy, sell, rebalance, or portfolio construction.
- Initial trend gate: explicitly define whether 3M, 6M, and 10M mean daily RSI periods or monthly RSI periods, then require all three RSI values to be above 50.
- Supporting fields: current price versus 20D/50D/200D moving averages, recent 1M/20-trading-day return, ATR percentage or another simple realized-volatility measure, data date, and missing-data status.
- Output: a transparent candidate table; do not hide the result behind an opaque composite score in the first version.
- Manual workflow: use the ranked list as a short-term continuation candidate list, then inspect the prior chart structure and accumulation/backdrop separately.

## Acceptance Criteria

- The scan runs against the current maintained watchlist without requiring a manually uploaded signal CSV.
- Every row exposes the multi-timeframe RSI values and the current-direction/volatility fields used for manual review.
- The result is reproducible for an explicit as-of date and does not use candles after that date.
- Missing or insufficient candle history is reported per symbol instead of silently excluding the stock.
- The strategy can later reuse the same trend, return, and volatility data for 52-week-high, risk-adjusted, and WIS experiments.

## Out of Scope

- Automated execution or portfolio rebalancing.
- Wyckoff phase classification inside the momentum scan.
- 52-week-high triggers, portfolio exits, automated entries, and WIS rebalance rules in the first version.
