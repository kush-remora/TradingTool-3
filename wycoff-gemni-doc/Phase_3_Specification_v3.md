# Product & Engineering Specification: Phase 3 "Dead Silent" Execution Filter (v3.0)

## 1. Product Overview & Objective
**Objective:** To systematically identify stocks entering Wyckoff "Phase C" (The Spring / Dead Silence). This is the exact moment an institution (The Composite Man) has fully absorbed the floating supply of an asset, resulting in an extreme, multi-day contraction in both trading volume and price volatility.

**The Problem with Legacy Logic:** Previous iterations relied on raw Delivery Percentages and standard Z-Scores. This failed because:
1. High-Frequency Trading (HFT) and day-trader noise mathematically crushed Delivery Percentages in trending stocks (The "Denominator Flaw").
2. Standard Z-Scores were "poisoned" by past Distribution (selling) phases at the top of the chart, making accumulation at the bottom look insignificant.

**The Solution:** We are implementing the **Wholesale Delivery Baseline** paired with a **Volatility Squeeze Guardrail (True Range)**. Instead of looking at total volume or blind averages, the system sorts for the absolute cheapest days the stock has seen recently, calculates the normal delivery volume for *only* those basement days, and flags an anomaly when delivery suddenly spikes at that floor.

---

## 2. Pipeline Execution Context
* **Data Ingestion:** End-of-Day (EOD) daily candle data (Open, High, Low, Close, Previous Close, Delivery Quantity). Note: Total Volume is intentionally bypassed for execution triggers.
* **Execution Scope:** This pipeline stage does **NOT** run on the entire market. It is executed manually by the user, strictly on a pre-filtered **"Watchlist"** of stocks that have already passed Phase 1 (Delivery Shocks) and Phase 2 (Accumulation Basing).
* **Output:** A flat, tabular dashboard displaying only the validated metrics.

---

## 3. Configurable Parameters (Dynamic by Market Cap)
The system must not hardcode lookback windows or structural limits. Product/Admin must be able to configure the following parameters per market cap tier (LARGE, MID, SMALL).

**Configuration Schema Definition:**
* `lookback_period_days` (Integer): The rolling window used to evaluate the price structure (e.g., 60 days).
* `basement_percentile` (Float): The percentage used to isolate the cheapest trading days in the lookback window (e.g., 0.10 for the bottom 10%).
* `density_rolling_window_days` (Integer): The lookback window to evaluate the current flatline pattern (The 'Y' in X of Y days).
* `density_qualification_days` (Integer): The minimum number of days required to be "quiet" to trigger a valid setup (The 'X' in X of Y days).
* `max_sma_distance_pct` (Float): The maximum allowable percentage deviation from the 200-Day SMA (e.g., 10.0 for $\pm 10\%$).

**Example Default Configuration for SMALL CAPS:**
* `lookback_period_days` = 60
* `basement_percentile` = 0.10
* `density_rolling_window_days` = 5
* `density_qualification_days` = 4
* `max_sma_distance_pct` = 12.0

---

## 4. Engineering Math & Logic Pipeline

For each stock in the Watchlist, execute the following steps in order using vectorized operations (e.g., Pandas).

### Step 1: Isolate the Structural Basement
Look at the closing prices over the `lookback_period_days` ($N$). Sort them from lowest to highest, and identify the price ceiling that defines the bottom `basement_percentile`.
$$Basement\_Threshold\_Price_t = Quantile(Close_{t-N \to t}, \text{basement\_percentile})$$

### Step 2: Calculate the Wholesale Delivery Baseline
Find the average **Absolute Delivery Quantity (DQ)** *only* on the days where the closing price was at or below the $Basement\_Threshold\_Price_t$.
$$Wholesale\_Base\_DQ_t = Mean(DQ \text{ WHERE } Close \le Basement\_Threshold\_Price_t)$$

### Step 3: Calculate the Accumulation Spike (The Proof of Life)
Divide today's actual Delivery Quantity by the clean Wholesale Baseline.
$$Accumulation\_Spike_t = \frac{DQ_t}{Wholesale\_Base\_DQ_t}$$

### Step 4: The Volatility Squeeze Guardrail (True Range Upgrade)
Volume/Delivery dry-up without price contraction is an abandoned asset (zombie stock). We must mathematically prove the daily price bar is compressing heavily, accounting for overnight gaps.
$$True\_Range_t = Max(High_t, Prev\_Close_t) - Min(Low_t, Prev\_Close_t)$$
$$Daily\_Volatility\_Pct_t = \left(\frac{True\_Range_t}{Close_t}\right) \times 100$$
$$Avg\_Volatility\_20d_t = Mean(Daily\_Volatility\_Pct_{t-20 \to t})$$
$$Spread\_Squeeze\_Pct = \left( \frac{Daily\_Volatility\_Pct_t}{Avg\_Volatility\_20d_t} \right) \times 100$$

### Step 5: The Context Guardrail (Trend Gravity)
The stock must not be in a Stage 4 markdown (falling knife). It must be resting near its long-term institutional average.
$$Distance\_to\_200SMA\_Pct = \left( \frac{Close_t - 200SMA}{200SMA} \right) \times 100$$
**Rule:** $Distance\_to\_200SMA\_Pct$ must be between $-max\_sma\_distance\_pct$ and $+max\_sma\_distance\_pct$.

---

## 5. Final Trigger Logic
The boolean flag `Phase_C_Trigger` evaluates to **TRUE** if and only if:
1. $Spread\_Squeeze\_Pct \le 50.0$ (Price is tightly coiled compared to its recent average)
2. $Close_t$ is within $\pm max\_sma\_distance\_pct$ of the 200-Day SMA.
3. $Accumulation\_Spike_t \ge 1.5$ (Today's delivery is at least 50% higher than the normal basement average).
4. *(Optional Pattern Check)* Count of days meeting criteria 1 & 2 over `density_rolling_window_days` is $\ge density\_qualification\_days$.

---

## 6. Dashboard UI Schema (Frontend Rendering)
The frontend table should strip away complex math and only show the operational truth to the user. Render the following columns for the Phase 3 Execution View:

| Column Header | Data Type | Formula / Origin Map | Format Example |
| :--- | :--- | :--- | :--- |
| **Symbol** | String | Ticker ID | `CLSEL` |
| **Market Cap** | Enum | Sourced from static DB | `SMALL` |
| **Today DQ** | Integer | $DQ_t$ | `125,302` |
| **Wholesale Base DQ** | Integer | $Wholesale\_Base\_DQ_t$ | `31,000` |
| **Accumulation Spike** | Float (x) | $Accumulation\_Spike_t$ | `4.0x` |
| **Volatility Squeeze** | Float (%) | $Spread\_Squeeze\_Pct$ | `40.3%` |
| **200 SMA Guard** | Boolean | Is price within `max_sma_distance_pct`? | `PASS` / `FAIL` |
| **Phase C Trigger** | Boolean | Final Trigger Logic Evaluation | 🔴 **READY** |
