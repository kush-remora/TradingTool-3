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

## Current discussion: attention-first ordering for the three-week watchlist

The 20-stock watchlist should be ordered by explainable research priority, not by a hidden buy/sell score or raw return. The first stock should be the one with the freshest and strongest market evidence requiring manual review. Each priority must show its reason, such as a recent high-volume event, repeated participation during the 90-calendar-day lookback, supportive weekly structure, or a current volume expansion versus the prior 10-session average.

The preferred v1 shape is now a watchlist summary table rather than a ranked queue. Each stock remains in watchlist order and receives independent, additive signal tags: near/at a 52-week high, recent 10-session volume anomaly, sustained weekly momentum around the 5% threshold, and higher-high/higher-low structure. A stock can satisfy multiple conditions simultaneously. The table is a navigation layer into the existing individual stock review, not a recommendation or a replacement for the raw evidence tables.

The volume signal must remain neutral: it should say that unusual volume and delivery deserve review, not automatically call the activity accumulation, distribution, or “big money.” The individual review supplies the price response, delivery, range, and structure needed for that interpretation.

The three-week review now also exposes completed-week ROC over a three-week lookback. ROC itself represents current momentum speed; the week-over-week change in ROC, shown in percentage points, represents acceleration. A rising ROC from negative is surfaced as `Rising from negative`, but ROC remains a separate sortable evidence field and does not replace the existing 5%-week, positive-week, 52-week-high, or structure signals.

## Current discussion: simple volume-event scanner v1

The separate v1 scanner is intentionally simpler than the momentum review. For each selected watchlist, look back 60 calendar days, use the prior 10 trading sessions as the volume baseline, and show the three largest qualifying volume events per stock where the event volume is at least 2× that baseline. The event review is evidence collection: date, age, event-day close, volume, multiplier, delivery percentage, current LTP, and price move since the event.

Accumulation/distribution labels, cycle detection, “safe to buy” conclusions, and a composite score are explicitly deferred. The user will first use this raw event view and provide examples before we add interpretation rules.
