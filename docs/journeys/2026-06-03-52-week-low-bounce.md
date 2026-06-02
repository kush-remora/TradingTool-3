# 52-Week Low Bounce Backtest Feature

## Feature Name
52-Week Low Bounce Backtest

## Why it was built
To evaluate a mean-reversion strategy of buying high-quality stocks when they hit their 52-week lows and observing how many days it typically takes to achieve a configured profit percentage (default 30%). This provides empirical data on the time-to-recovery for extreme drawdowns in strong stocks.

## What was implemented
1. **Backend Model & Logic**: Added `FiftyTwoWeekLowBacktestService` to filter stocks with enough trading history (>252 days) and simulate trades starting exactly when a rolling 252-day low is breached.
2. **REST Endpoint**: Exposed `POST /api/strategy/52-week-low/backtest` via `StrategyResource` to accept user parameters like the index to scan, lookback period in days, and a target profit percentage.
3. **Frontend Integration**: Created `use52WeekLowBacktest` hook and `FiftyTwoWeekLowBacktestPage` with Ant Design components (Select for Index, InputNumber for lookback and profit percentage, and an interactive Table).
4. **Data Export**: Built-in Markdown export feature directly from the UI for external reporting.

## Key Decisions & Trade-offs
- Used daily close data alongside daily highs to determine if a target profit percentage was achieved intraday, modeling a GTT order style execution without needing tick-by-tick data.
- Enforced a hard minimum of 252 trading days (1 year) of history before the lookback window to ensure the initial 52-week low calculation is valid, excluding new IPOs.
- Held positions remain open indefinitely in the backtest (no stop loss) to explicitly observe the maximum recovery time or failure to recover, as per the requested specification.

## Validation Run & Outcomes
- Ran `mvn clean compile -DskipTests` to confirm the Kotlin backend changes are syntactically and architecturally sound without compilation errors.
- Ran `npx tsc --noEmit` on the React frontend to verify that the newly added types (`types.ts`) and components (`FiftyTwoWeekLowBacktestPage.tsx`) had no typescript violations.

## Next Follow-ups
- Run the scanner on actual production data against `NIFTY 500` over a 5-year lookback period to analyze performance and memory load.
- Observe if any memory pressure arises from loading a large number of candles simultaneously, and consider parallelizing the stock-by-stock calculation flow if needed.
