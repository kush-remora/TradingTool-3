# Strategy Research Map

**Created:** April 11, 2026  
**Sources:** `Gemini chat/*.pdf`, `docs/strategies/*`

## Purpose

Turn the current trading notes into something we can actually build, test, and review without mixing together:

- strategy idea,
- signal filter,
- ranking logic,
- entry trigger,
- and exit/risk rules.

## 1. North Star

The real goal is not "find clever chart patterns." The goal is:

- grow capital consistently,
- control drawdowns,
- avoid large false-positive trades,
- and build a system simple enough to trust and improve.

For this repo, that means:

- rule-based decisions,
- daily or weekly workflows over intraday screen-watching,
- reusable data pipelines,
- and strategies that can be backtested before they are trusted.

## 2. Current Reality

### What the Gemini material actually contains

The `Gemini chat` PDFs are mostly chat transcripts. They explain observed chart setups, but they are not a reusable chart library and they are not backtest evidence.

That means we currently have:

- good hypotheses,
- repeated strategy themes,
- some useful filters,
- but not yet a clean research program.

### What already exists in this codebase

- `Remora`: daily hidden-accumulation / hidden-distribution scanner using price, volume, and delivery data.
- `Weekly Pattern Screener`: weekday-based swing analysis and live rebound trigger guidance.
- `Pre-Earnings Momentum`: detailed concept note exists, but there is no clear earnings-calendar pipeline in the repo yet.

### Important conclusion

We do **not** have one single strategy. We have at least five different strategy families that are currently being mixed together.

## 3. Strategy Families

| Family | Core idea | Typical holding period | Current repo overlap | Recommendation |
| --- | --- | --- | --- | --- |
| Weekly RSI Momentum Portfolio | Rank liquid stocks by multi-period RSI and rebalance weekly | Weeks to months | No direct implementation yet | Build as a separate portfolio module |
| Extreme RSI Mean Reversion | Buy very rare oversold conditions near long-lookback RSI lows | Days to weeks | No direct implementation yet | Keep as research first, not first live strategy |
| Accumulation -> Breakout Hybrid | Detect quiet accumulation first, then buy only after momentum confirmation | 2-10 trading days | Strong overlap with Remora and delivery pipeline | Best next live swing strategy |
| Pre-Earnings Momentum | Use the earnings window as a catalyst and enter only when accumulation appears before results | 5-14 days | Concept doc only | High potential, but blocked by earnings data pipeline |
| Weekly Seasonality Timing | Learn which weekday tends to offer the best entry/exit profile | 2-5 trading days | Already implemented | Keep as a timing overlay, not the main engine |

## 4. The Five Families in Plain English

### A. Weekly RSI Momentum Portfolio

This is the Alok Jain / Weekend Investing style idea.

Core shape:

- use a liquid stock universe such as Nifty 100 / Nifty 500,
- calculate RSI across multiple lookbacks,
- rank the universe every week,
- hold the highest-ranked stocks,
- remove names that lose rank,
- optionally move to cash or liquid ETF in bad market regimes.

What makes this attractive:

- simple weekly workflow,
- easy to understand,
- naturally diversified,
- lower dependence on one perfect chart.

What makes it different from the other ideas:

- this is a **portfolio ranking system**, not a single-stock trigger engine.

Minimum version we should build:

- fixed universe,
- daily OHLC fetch,
- RSI 22/44/66 ranking,
- weekly rebalance report,
- optional market regime filter later.

### B. Extreme RSI Mean Reversion

This is the "buy near 200-day minimum RSI" idea.

Core shape:

- stock reaches a rare multi-month RSI extreme,
- volume spikes,
- selling looks exhausted,
- and price shows some form of reversal before entry.

What is good about it:

- entries can be cheap,
- upside can be large when panic reverses fast.

What is dangerous about it:

- it can become a falling-knife strategy,
- false positives are expensive,
- and fundamentals or news can permanently change the stock's behavior.

Recommendation:

- do not launch this as a standalone production strategy first,
- keep it as a research scanner or secondary watchlist,
- require confirmation like divergence, reversal candle, or reclaim of recent highs.

### C. Accumulation -> Breakout Hybrid

This is the strongest repeated idea across the Gemini chats.

Core shape:

1. Detect hidden accumulation:
   - high delivery,
   - high volume,
   - flat or muted price action.
2. Do **not** buy immediately.
3. Wait for public confirmation:
   - breakout above accumulation-zone high,
   - rising RSI,
   - renewed volume,
   - and preferably relative strength.

Why this is important:

- it avoids guessing the exact bottom,
- it matches the existing Remora intuition,
- it gives a cleaner state machine:
  - `SCOUTING`
  - `ACCUMULATING`
  - `READY_FOR_TRIGGER`
  - `ACTIVE`
  - `EXITED`

This is the best next strategy to operationalize because:

- delivery data is already in the repo,
- Remora already detects quiet institutional behavior,
- and the missing piece is mainly the trigger and trade lifecycle layer.

### D. Pre-Earnings Momentum

This is the event-driven catalyst strategy.

Core shape:

- maintain a watchlist plus earnings calendar,
- scan only in the `T-21` to `T-1` window,
- look for accumulation without broad market or sector weakness,
- enter when the pre-results setup is confirmed,
- exit with rule-based stop logic and forced time exit around `T+1`.

Why it is attractive:

- the catalyst is explicit,
- holding period is short,
- and it fits your observation about "buy the rumour, sell the fact."

What blocks it right now:

- no clear earnings calendar ingestion path,
- no event-history dataset for backtesting,
- and no existing module for catalyst-aware scans.

Recommendation:

- treat this as phase two or three,
- after the shared scanner and trade-state plumbing are stable.

### E. Weekly Seasonality Timing

This already exists and should stay in the system.

What it is good for:

- deciding *when* during the week to pay attention,
- adding context to existing watchlist names,
- and avoiding random entry days.

What it is not:

- a complete wealth-building system by itself,
- or a substitute for trend, catalyst, or quality filters.

Best role:

- timing overlay on top of stronger primary strategies.

## 5. What We Should Build First

### Recommended order

1. **Accumulation -> Breakout Hybrid**
2. **Weekly RSI Momentum Portfolio**
3. **Pre-Earnings Momentum**
4. **Extreme RSI Mean Reversion research scanner**

### Why this order makes sense

#### 1. Accumulation -> Breakout Hybrid first

This reuses the most existing work:

- `Remora` already detects quiet accumulation/distribution behavior.
- Delivery ingestion already exists.
- The current backend pattern already supports scanner -> persistence -> API -> frontend.

What is missing:

- breakout trigger rules,
- signal state transitions,
- trade invalidation rules,
- and a simple review UI.

#### 2. Weekly RSI Momentum Portfolio second

This should stay separate from the breakout engine because it solves a different problem:

- portfolio selection,
- not swing-trade triggering.

If we mix these too early, the logic will become muddy fast.

#### 3. Pre-Earnings third

This likely has real edge, but it depends on a clean event calendar and event-history backtest. Without that, we would be building a story, not a system.

#### 4. Extreme RSI last

This should earn its place through data. Right now it is the most emotionally attractive and the least safe to trust without evidence.

## 6. Shared Building Blocks

Before adding many new strategies, we should reuse a small shared set of metrics:

- daily OHLCV,
- delivery percentage and delivery ratio,
- RSI across multiple windows,
- ATR 14,
- rolling 20-day high and low,
- 200-day moving average,
- sector mapping,
- benchmark relative strength,
- earnings dates.

These should be shared inputs, not copied strategy-by-strategy.

## 7. Architecture Fit for This Repo

The current repo already points toward a simple pattern:

1. scanner/service computes signals,
2. results are persisted idempotently,
3. REST endpoint exposes the latest state,
4. frontend renders review and decision support.

That means:

- use `SignalScanner` for scanners like `Remora`, `PreEarningsScanner`, or `CapitulationScanner`,
- keep portfolio ranking separate from scanners,
- keep trade lifecycle logic separate from signal detection,
- avoid one giant "mega strategy" service.

### Suggested split

- **Scanner**: discovers candidate setups
- **Ranker**: orders candidates or portfolio members
- **Trade manager**: applies stop, target, and time-exit rules
- **Review UI**: shows why a setup exists and what would invalidate it

This keeps the code understandable six months from now.

## 8. Chart Review Checklist

When we review a chart manually, use the same checklist every time:

1. **Universe**
   - Is this a liquid, tradable stock from an allowed universe?
2. **Market context**
   - Is the index weak, strong, or neutral?
3. **Sector context**
   - Is the stock moving on its own, or is the whole sector moving?
4. **Trend context**
   - Is price above or below the 200-day moving average?
5. **Accumulation evidence**
   - Do we see high volume, high delivery, and muted price movement?
6. **Momentum evidence**
   - Is RSI improving, crossing a useful threshold, or diverging positively?
7. **Trigger**
   - What exact event makes this a buy?
   - Example: reclaim of accumulation high, break of 20-day high, RSI confirmation.
8. **Invalidation**
   - What price level proves the setup is wrong?
9. **Exit plan**
   - Fixed target, trailing stop, or time exit?
10. **Reason for move**
   - Is there a clean catalyst, or are we only telling ourselves a story after the fact?

If we cannot answer these clearly, the chart is not ready to become a strategy rule.

## 9. Questions That Need Your Input

These are the highest-value questions left open:

1. Do you want the system to make money mainly through:
   - a weekly portfolio model,
   - a swing alert engine,
   - or both?
2. What is your preferred holding period for the first live strategy?
   - `2-5 days`, `1-2 weeks`, or `multi-week`
3. What is the maximum acceptable loss per trade?
4. Are you comfortable entering **after** confirmation instead of trying to buy the exact bottom?
5. Which universe should be the default:
   - Nifty 100,
   - Nifty 500,
   - or your custom watchlist?

## 10. Final Recommendation

If the goal is to become consistently profitable with lower risk, the cleanest path is:

- use `Remora`-style accumulation as the **watchlist builder**,
- add a breakout trigger as the **entry gate**,
- use weekly seasonality as a **timing helper**,
- and keep the pure bottom-fishing RSI ideas in research until backtests prove they deserve capital.

That path is simple, close to the current architecture, and much safer than trying to build every idea at once.
