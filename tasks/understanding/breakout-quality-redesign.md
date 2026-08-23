# Breakout Quality Redesign — Research Proposal

## Current understanding

The existing `BreakoutDayQualityAnalyzer` is not a useful quality filter. It scores a breakout from a short, mostly five-session window using buyer-days, close position, pre-breakout volume dry-up, and breakout-day close position. Delivery is reported as context, but neither the score nor its supporting data has improved breakout outcomes in backtests. This proposal therefore treats the current quality model as deprecated; it should not be extended or tuned.

The replacement should investigate why a breakout fails using historical, pre-breakout evidence across multiple windows. It should begin as a forensic dataset and explanation panel, not a combined score or hard entry gate. A feature earns implementation priority only if it improves a pre-declared failure/outcome measure in walk-forward or out-of-sample testing.

## Research conclusions

The recurring ideas across academic work and established technical methodology are:

1. **Acceptance above the level:** A price poke is not enough. A breakout that closes back inside the prior range soon after the event is a false breakout. The broken resistance becoming support and surviving a retest is stronger evidence, but it is a post-breakout confirmation and must not leak future information into same-day scoring.
2. **Relative participation, not raw delivery percentage:** Volume should be measured against the stock's own historical activity. Delivery percentage is a denominator-sensitive statistic: a lower percentage can coexist with a much larger number of delivered shares when total volume expands. Delivery quantity, traded quantity, rupee turnover, and their historical ratios should remain separate fields; delivery percentage should not be a primary quality rule.
3. **Base structure:** Breakouts are more vulnerable when the base is too short, too wide, volatile, loose, repeatedly rejected, or already late in a mature advance. Base duration, normalized width, volatility contraction, and prior resistance tests should be measured over the full available history.
4. **Overhead supply and room to move:** A breakout immediately below older high-volume price zones or major swing resistance has less room. Any volume-by-price or resistance calculation must use only candles available on the breakout date to avoid look-ahead bias.
5. **Relative strength and regime:** A stock breaking out alone while its sector, benchmark, or market regime is weak is a different setup from a stock participating in a broad uptrend. This should be tested as context, not assumed to be universally predictive.
6. **Liquidity and execution:** Thin or highly illiquid stocks can create large-looking daily moves with unreliable acceptance, high impact, and gap risk. A simple turnover/impact diagnostic is more defensible than interpreting delivery as institutional intent.
7. **Research discipline:** Technical-rule research is especially exposed to data snooping, changing market regimes, and transaction-cost sensitivity. We must test features separately, preserve a locked holdout period, and record unsuccessful hypotheses.

## New hypotheses from the V-recovery question

The phrase “V-recovery breakout” is useful as a hypothesis, but it is not an established universal rule that every V-recovery breakout fails. The more precise distinction is:

- **Fast reclaim without a new base:** price falls sharply from resistance, returns quickly to the old level, and attempts another breakout without spending meaningful time consolidating. This can still carry trapped sellers, overhead supply, and insufficient absorption. A sharp V-shaped recovery can also be a valid reversal, so this should be measured as elevated risk rather than automatic rejection.
- **Fresh base after the failure:** price spends enough time below resistance to form a new, tighter structure, with reduced volatility and controlled tests. This is a different setup and should not inherit the same penalty merely because an older breakout failed.
- **Repeated tests from a constructive base:** repeated approaches are not automatically bearish. A stable base, higher lows, narrowing ranges, and lower-volume pullbacks can represent supply absorption. Repeated high-volume rejections, wide ranges, and fast recoveries without a new base are the adverse version.

The proposed features are therefore `prior_breakout_failure_recency`, `days_since_failure`, `post_failure_base_duration`, `post_failure_reset_depth`, `new_base_width`, `rejection_count`, `rejection_volume_vs_baseline`, and `recovery_speed_to_resistance`. We should compare these groups directly instead of applying a simple “one previous failure means reject” rule.

## New hypotheses for a large pre-breakout volume bar

A 5x or 10x volume bar during an otherwise quiet base is worth investigating, but it does not prove that informed institutions accumulated shares. The same bar can represent news, panic liquidation, a block trade, index rebalancing, operator activity, or distribution. The useful signal is the relationship between effort and result:

- high volume + wide up-spread + close near the high + price holds the gain;
- high volume + narrow spread or limited downside near support, followed by stabilization;
- high volume + wide spread but weak close or immediate reversal;
- high volume that is not followed by any acceptance or continuation.

The volume study should record percentile/ratio versus the stock's 60/120-session history, candle spread in ATR units, close location, direction, price retention over the next 3–10 sessions, and time to breakout. A single raw multiplier should not be promoted to a rule until these event types show different failure rates in the NSE data.

Established chart methodology similarly treats a sharp V-like bottom cautiously, prefers a developed base, and expects volume expansion to be interpreted together with the price structure rather than in isolation. This is a practitioner hypothesis, not proof of a universal V-recovery law.

## Proposed replacement: Breakout Failure Forensics

Do not create another PASS/WAIT/REJECT score initially. For every breakout, store:

- a primary failure label: **close-back-inside-range within 3 sessions**;
- secondary labels: **failed follow-through within 10 sessions**, **stop-before-target**, and **successful retest/acceptance**;
- the pre-breakout evidence used to explain the label;
- data availability and lookback lengths;
- the market and sector context on the signal date.

The first feature families to investigate are:

- **Structure:** base duration; base width divided by price and ATR; ATR/range percentile; contraction from the earlier base; count and spacing of resistance tests; distance from the breakout level to the nearest prior resistance/volume node.
- **Price acceptance:** breakout close distance above the boundary in ATR terms; close-location value; gap size; whether the candle closes back in the range on T+1/T+3. The last item is an outcome label, not an entry-time feature.
- **Participation:** breakout volume and rupee turnover versus rolling median/percentiles; pre-breakout volume trend; whether participation persists over T+1/T+3; delivered quantity versus its own history. Delivery percentage is diagnostic only.
- **Trend and relative strength:** 20/60-session performance versus NIFTY and the stock's sector; distance from moving averages; trend slope and volatility-adjusted momentum.
- **Supply/liquidity:** overhead resistance, high-volume price zones, average rupee turnover, zero-range/circuit-like sessions, and an Amihud-style absolute-return/rupee-turnover proxy where the data supports it.
- **Regime:** benchmark trend, benchmark volatility percentile, sector participation, and a simple broad-market state.

## Implementation sequence

1. Freeze the current quality score as legacy context; remove it from any future filtering decision.
2. Define one primary failure label and one fixed evaluation horizon before inspecting feature thresholds.
3. Build a historical event table with only information available at each breakout date. Keep raw inputs and derived features separate.
4. Produce outcome rates by feature buckets, with minimum event counts and confidence intervals. Start with univariate evidence; avoid a model until we know which dimensions carry signal.
5. Validate candidate rules with chronological walk-forward splits, a locked holdout period, costs/slippage, and results segmented by market-cap bucket and market regime.
6. Implement one evidence-backed diagnostic at a time. Prefer named risk flags with explanations over a single opaque score.
7. Only after paper-trading validation should any flag become a hard gate. Quality-filter work remains separate from the Phase 1 anomaly scanner.

## Explicitly excluded for this redesign

- delivery-percentage thresholds;
- the current five-session buyer-day/close-position score;
- automatic rejection after any previous breakout failure;
- automatic rejection of every V-shaped recovery;
- treating every repeated resistance test as bearish;
- treating every 5x/10x volume bar as institutional accumulation;
- a fixed “40% volume” or similar threshold copied from another market;
- RSI/MACD/OBV indicator stacking without incremental backtest evidence;
- same-day use of next-day confirmation;
- tuning thresholds to maximize win rate on the existing backtest sample.

## Research references

- Andrew Lo, Harry Mamaysky, and Jiang Wang, *Foundations of Technical Analysis* (Journal of Finance, 2000): https://web.mit.edu/Alo/www/articles.html
- Cheol-Ho Park and Scott H. Irwin, *What Do We Know About the Profitability of Technical Analysis?* (Journal of Economic Surveys, 2007): https://doi.org/10.1111/j.1467-6419.2007.00519.x
- Narasimhan Jegadeesh and Sheridan Titman, *Returns to Buying Winners and Selling Losers* (Journal of Finance, 1993): https://doi.org/10.1111/j.1540-6261.1993.tb04702.x
- Tobias Moskowitz, Yao Ooi, and Lasse Pedersen, *Time Series Momentum* (Journal of Financial Economics, 2012): https://pages.stern.nyu.edu/~lpederse/papers/TimeSeriesMomentum.pdf
- Yakov Amihud, *Illiquidity and Stock Returns* (Journal of Financial Markets, 2002): https://doi.org/10.1016/S1386-4181(01)00024-6
- Campbell, Grossman, and Wang, *Trading Volume and Serial Correlation in Stock Returns* (Quarterly Journal of Economics, 1993): https://campbell.scholars.harvard.edu/publications/trading-volume-and-serial-correlation-stock-returns
- Bajcsy and Scaillet, *Technical Trading Revisited: False Discoveries, Persistence Tests, and Transaction Costs*: https://www.scaillet.ch/pdfs/BajSca.pdf
- StockCharts, *Scanning for Consolidation and Breakouts*: https://help.stockcharts.com/scanning-and-alerts/writing-scans/scanning-for-consolidation-and-breakouts
- Investor's Business Daily, *Buying Checklist*: https://www.investors.com/wp-content/uploads/2025/10/IBD_BuyingChecklist_2025_10.pdf

## Current decision

No application code is changed by this proposal. The next implementation task should be the event-table and failure-label work, not a new quality score.
