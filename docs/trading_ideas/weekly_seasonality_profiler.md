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
