---
name: technical-analysis-ta4j
description: Institutional standard mathematical constraints and architectural rules for building or modifying Technical Indicators (RSI, MACD, SMA, etc.) in the TradingTool suite.
---

# Technical Analysis & Indicator Philosophy

When calculating moving averages, oscillators, or volatility metrics within the TradingTool ecosystem, you MUST strictly adhere to the following institutional boundaries.

## 1. NEVER Write Custom Math Logic
Do **NOT** write manual loops (e.g. tracking `avgGain` and `avgLoss` via `for` loops) to calculate standard indicators like RSI, SMA, EMA, MACD, or ATR.
- **Why?** Custom mathematical loops are prone to edge-case bugs, array boundary overflows, and diverge subtly from standard charting software.
- **Action:** ALWAYS utilize the `org.ta4j:ta4j-core` library primitives which is fully bundled into the `core/pom.xml`.

## 2. Exponential Decay Smoothing Convergence (CRITICAL)
Indicators like RSI, EMA, and MACD rely on Wilder's Smoothing Method (SMMA) or Exponential Decay. These formulas possess "infinite memory"—meaning today's RSI value is mathematically influenced by price action from years ago.
- **The Trap:** If you fetch only `today.minusDays(70)` or `today.minusDays(365)` from the database to calculate today's RSI, your metric will be mathematically orphaned and heavily suppressed (sometimes off by 10-15 peak points) relative to institutional APIs like TradingView.
- **The Rule:** Whenever dynamically instantiating an RSI or EMA constraint bound, you **MUST** query the SQL database for a massive warmup payload (e.g., `today.minusYears(5)`) to feed the `ta4j` `BarSeries`. The first 250+ intervals are required strictly for mathematical convergence before you begin reading the realistic current values.

## 3. Data Type Pipeline
Always transform database queries directly into `ta4j` components before acting on them:
1. Map `List<DailyCandle>` into a `BaseBarSeriesBuilder`.
2. Construct your `ClosePriceIndicator` (or High/Low variant).
3. Compute the `RSIIndicator(closePrice, 14)`.
4. Read values cleanly mapped against the target array indices.

## 4. Standard Parameters
Do not deviate from globally accepted parameters unless explicitly instructed by the user strategy:
- **RSI:** 14 Periods.
- **MACD:** 12, 26, 9.
- **Bollinger Bands:** 20, 2.

## 5. Handle Padding Initialization Exceptions
Remember that `ta4j` requires minimum array sizes to execute valid indicator values. For index positions smaller than the defined parameter (e.g. index 0 to 13 on a `14-period` RSI), `ta4j` will output `NaN`, `0.0`, or throw initialization errors.
- Always provide a clean fallback (like `50.0` or `null`) when unpacking parallel array streams to prevent null pointer and `IndexOutOfBoundsException` API crashes on newly IPO'd stocks (with fewer than 15 historical trading days).
