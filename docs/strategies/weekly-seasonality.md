# Idea: Weekly Seasonality Profiler

**Date:** March 2026
**Created By:** Kush & PM Priya

## 1. The Initial Observation
Observations indicated that certain stocks (e.g., Netweb Technologies) exhibit a consistent weekly cycle. For instance, sometimes dropping to a low early in the week (e.g., Monday dropping to 3050-3100) and rising by 5-7% later in the week (e.g., Wednesday peaking around 3300-3400).

However, assuming a rigid "Monday-to-Wednesday" bounce across all stocks is flawed. Different stocks have different intra-week seasonality based on various factors. The best approach is to let the data dynamically dictate the pattern for each stock.

## 2. Feature Spec: Weekly Seasonality Profiler

### Problem Statement
Different stocks exhibit different intra-week cycles based on institutional buying patterns, option expiries, or sector dynamics. We need an automated way to discover the *most probable* weekly bottom and peak days for any given stock in our watchlist, so we can time our swing trades optimally.

### User Story
As a trader, I want to input a stock ticker and have the system tell me exactly which day of the week it usually hits its lowest price, which day it hits its highest price, and the average percentage swing between those two days over the last 1-2 months.

### Acceptance Criteria
- The system accepts a predefined static list of up to 15 stock symbols.
- It fetches historical daily OHLC (Open, High, Low, Close) data for the last 8-12 weeks.
- It groups the trading data into distinct calendar weeks.
- **Pattern Identification Logic:**
  - For each week, it records the exact day (Mon - Fri) the weekly Low occurred, and the day the weekly High occurred.
  - It calculates the mode (most frequent) Low Day and most frequent High Day over the entire lookback period.
- **Scoring & Output:**
  - It outputs the identified pattern: e.g., *"Typically bottoms on Tuesday, peaks on Friday."*
  - It outputs the **Confidence Score**: e.g., *"Tuesday was the weekly low 70% of the time. Friday was the high 60% of the time."*
  - It outputs the **Average Expected Swing**: The average % return from the typical low day to the typical high day.
- **Noise Cancellation:** If a stock's weekly lows and highs are scattered randomly across different days (confidence score < 40%), the system flags the stock as "No clear weekly pattern."

### Technical Considerations
- **Holiday Handling (Gotcha):** Indian markets have frequent weekday holidays. When grouping by day-of-week, the logic must account for 4-day or 3-day weeks so it doesn't break the frequency counters.
- **Data Source:** Reuse the existing Dropwizard backend and the Kite Connect API to fetch the historical data. To respect Kite's rate limits, fetch this data once after market hours and cache it in the existing Supabase (PostgreSQL) database.
- **Implementation:** Build this logic as a standalone Kotlin service class (e.g., `SeasonalityAnalyzerService`) so you can unit test it thoroughly by passing in mock OHLC arrays before wiring it to Kite or the DB. Expose it initially via a simple `GET /api/swing-analyzer` endpoint.

### Out of Scope
- Auto-discovery/screener functionality to find these stocks out of the Nifty 500.
- Intraday minute-by-minute analysis (this strictly uses daily candles).
- Automatically trading the pattern (you will still place the trades manually).

### Complexity Estimate
- **2 Days.** The math itself is simple, but writing the Kotlin logic to properly group weeks, handle market holidays, and calculate the frequency distribution will take some focused time. Perfect size for a weekend.

---

## 3. Implemented Algorithm (Current)

This section documents the logic currently used by `/api/screener/weekly-pattern` in simple terms.

### In Plain English

For each stock:

1. We pull daily candles and split them into completed weeks.
2. We try every valid buy/sell weekday pair inside a week:
   - Buy day candidates: Mon, Tue, Wed, Thu
   - Sell horizon candidates: any later day in the same week
3. For each week and each pair, we simulate trade rules:
   - We do **not** buy at the exact low.
   - Entry is only if buy-day low gets a **+1% rebound**.
   - If RSI context says overbought, skip that entry.
   - After entry:
     - hit stop-loss -> exit immediately
     - else hit target -> exit immediately
     - else exit at configured sell horizon close (hard exit)
4. We score each weekday pair by consistency + win rate + swing size.
5. The best-scoring pair becomes that stock’s weekly pattern.

### Why Monday->Friday Can Still Show "Target Hit"

`sellDay` is the latest allowed hard-exit window, not necessarily the day the trade actually exits.
If target/SL is hit earlier (for example Tuesday), that week is still counted as a Tuesday exit internally.

### Detailed Rules

#### Inputs

- `lookbackWeeks`: number of completed weeks to evaluate
- `minTradingDaysPerWeek`: skip sparse weeks
- `entryReboundPct`: current entry trigger (default 1.0)
- `swingTargetPct`: default target %
- `stopLossPct`: stop-loss %
- `patternConfirmed` thresholds:
  - min entry rate
  - min win rate
  - min average swing

#### Week Simulation (per candidate pair)

Given buy day `B` and sell horizon day `S`:

1. Get buy-day candle and sell-day candle.
2. Compute potential entry:
   - `entry = buyLow * (1 + entryReboundPct/100)`
3. Entry is valid only if:
   - buy-day high >= entry
   - adaptive RSI is not overbought
4. After entry, scan days `B+1 ... S`:
   - if day low <= stop-loss level -> stop-loss exit
   - else if day high >= target level -> target exit
5. If neither hit by `S`, exit at `S` close (hard exit).

### Pair Scoring

For each candidate pair:

- `entryRate = entries / eligibleWeeks`
- `winRate = targetHits / entries`
- `avgSwing = average realized swing on entered weeks`

Composite score:

- `entryScore = entryRate * 40`
- `winScore = winRate * 40`
- `magnitudeScore = scaled(avgSwing) * 20`
- `composite = entryScore + winScore + magnitudeScore`

Best pair selection:

1. higher `composite`
2. then higher `avgSwing`
3. then higher entry consistency

### Output Fields Meaning

- `buyDay`: most probable entry day from best pair
- `sellDay`: typical realized exit day (target/SL/hard-exit behavior)
- `reboundConsistency`: count of weeks where entry was triggered
- `swingConsistency`: count of entered weeks where target was hit
- `avgPotentialPct`: weekly raw low->high swing (upper-bound context, not tradable as-is)
- `weeklyHeatmap[].reasoning`: exact per-week exit reason
  - `Target Hit ... on Tue (...)`
  - `Stop Loss Hit ...`
  - `Thu Hard Exit`

### Important Limitation

This is a daily-candle backtest. If both SL and target are touched in the same candle, we use conservative sequencing (SL first). It is robust for direction and consistency, but still an approximation of intraday path.
