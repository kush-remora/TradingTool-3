# Weekly Floor Rebound — Product Understanding

## Current Understanding

The idea is to find a stock that has stopped making meaningful new lows after a prior decline, forms a narrow three-week support area, and then rebounds from that area. The original goal of a guaranteed 5% weekly gain is rejected: the strategy must be judged by expectancy, drawdown, fill rate, and the size of losses, not by a 100% win rate.

This is initially a single-stock research experiment, not a scanner and not yet a production strategy. A repeated price floor alone is not evidence of Wyckoff accumulation; it is a price-only hypothesis. Any later strategy must add supply-exhaustion and absorption evidence before describing a candidate as institutional accumulation.

## Polished Hypothesis: Three-Week Floor Rebound

On the first trading day of each week, a stock that has formed a tight support floor during the prior three completed weeks can produce a +5% swing more often than its defined structural stop is hit. The entry must occur only after price recovers above the floor; no trade is taken if it never triggers.

### Price Definitions

- **Base floor (B):** the lowest low of the prior three completed calendar weeks.
- **Floor tightness:** `(highest of the three weekly lows / B) - 1 <= 2.0%`. This replaces an exact rupee-level rule, which does not scale across stock prices or volatility.
- **Recovery entry (E):** `B × 1.01` — a stop-buy trigger, not a passive limit order. A passive limit would normally fill below the trigger and would test a different idea.
- **Structural stop (S):** `B × 0.995` (0.5% below the floor). The planned loss from entry is about 1.49%, before gaps and costs.
- **Target (T):** `E × 1.05` (+5% from entry).
- **Maximum holding period:** exit at the close of the Friday in the entry week if neither target nor stop has occurred. If Friday is not a trading day, exit at the close of that week's final trading day.

### Initial Eligibility Filters

- Use one liquid NSE equity with a complete daily OHLC history; do not start with universe-wide screening.
- **Not near a 52-week high:** on the final trading day before the entry week, `close <= 90% × highest high of the prior 252 trading sessions`. A stock 10% or more below its 52-week high is eligible; one within 10% is excluded. This prevents the test from accidentally becoming a late-stage momentum strategy, but does not itself prove value or safety.
- **Prior decline into the base:** find the highest daily high from 60 to 16 trading sessions before the entry week. Require that high to be at least 10% above the base floor: `prior high >= 1.10 × B`. The three-week floor then shows that the decline has paused; it does not require the price to be rising already. This prevents a normal uptrend from being mislabeled a floor. The 60-session lookback and 10% decline are provisional and must be sensitivity-tested.
- Skip earnings-result dates and obvious corporate-action data discontinuities in the first pass; log them rather than silently deleting them.

## Backtest Contract

- Evaluate each eligible week independently using only information available before that week opens.
- On the first trading day, fill at E only if that day's high reaches E. A gap above E fills at that day's open, which captures adverse gap slippage.
- Start stop/target evaluation from the next trading day. Daily OHLC cannot reliably order an entry, stop, and target that occur inside the same candle.
- If a later daily candle reaches both S and T, record the conservative outcome: stop first. Also report the count of these ambiguous days.
- Apply delivery charges, brokerage, STT, exchange charges, GST, and a configurable slippage estimate before reporting net results.
- Report every setup, including no-fill weeks, stopped trades, time exits, and gap-stop exits. Do not report only winners or only filled trades.

## Success Measures

The experiment passes only if it has enough independent samples and produces a positive result after costs. The first report should show:

- number of eligible weeks, fills, fill rate, target hits, stop hits, time exits, and ambiguous daily bars;
- gross and net win rate, average win, average loss, profit factor, expectancy per filled trade, and maximum drawdown of sequential trades;
- median holding period and the distribution of maximum adverse and favourable excursion;
- sensitivity for 1%, 1.5%, and 2% floor tightness, and for 3%, 5%, and 7% targets;
- an out-of-sample split: choose rules on an earlier period and judge them unchanged on a later period.

There is no minimum win-rate target. With the planned 5% reward and roughly 1.5% defined risk, the break-even win rate before costs is about 23%, but real gap losses and time exits mean the actual bar must be demonstrably higher.

## Wyckoff Upgrade Path (Only If Price Test Survives)

If the price-only hypothesis is positive, add evidence in this order:

1. Prior markdown into the floor, followed by reduced downside spread or volatility.
2. Delivery-volume or total-volume evidence of effort-versus-result: elevated participation without continued price progress downward.
3. Support/test behavior: a lower-volume retest that holds above or near the floor.
4. Later confirmation through a sign of strength; do not label the initial rebound a completed accumulation event.

## Decision and Next Step

Use this as a narrow falsifiable test rather than assuming 5% is repeatable. The next task is to select one stock and its historical period, then build a daily-OHLC backtest with the above execution rules. NETWEB is a sensible first candidate because an existing Monday-bounce research thread already covers it, but it should be used only if Kush confirms it is the intended stock.

## Implementation Outcome — 2026-07-24

V1 is implemented as a reusable Kotlin backtest engine, an on-demand API at `POST /api/strategy/weekly-floor-rebound/backtest`, and a frontend page named **Weekly Floor Rebound**. NETWEB is the UI default, but the existing Kite instrument search permits selection of any NSE equity. The engine evaluates the most recent 200 completed trading sessions and reads older candles only for the 252-session context filter.

The implementation reports every completed week, including ineligible and no-entry weeks, rather than presenting only trades. It excludes an unfinished current week, uses a conservative stop-first decision when daily OHLC reaches both stop and target, and exits a valid unresolved trade at the final trading close of that week. Gross-return-only reporting remains intentional for V1.
