# Bollinger Squeeze Strategy — Design Decisions

**Status:** Draft Strategy  
**Base Indicator:** Bollinger Bands (20, 2)  
**Core Concept:** Volatility contraction precedes volatility expansion.

---

## 1. Strategy Concept

### The "Squeeze"
- A "Squeeze" occurs when the Bollinger Bands contract to an unusually tight level.
- **Definition:** The current Bollinger Bandwidth is the **lowest in the last 60 trading days**.
- **Market Context:** Periods of extremely low volatility often lead to explosive price moves. This strategy seeks to identify these quiet periods and enter as the "breakout" begins.

### The "Expansion" (The Breakout)
- We are looking for a high-momentum candle that breaks the squeeze.
- We prefer breakouts that occur in the direction of the underlying trend.

---

## 2. Design Principles

| Principle | Decision |
|-----------|------------|
| **Setup** | Strictly "Lowest 60-day Bandwidth". |
| **Trend** | Trade only in the direction of the primary trend (e.g., above SMA 200). |
| **Trigger** | Price breaking out of the upper band with high volume. |
| **Simplicity** | Use Daily OHLC data only (consistent with Strategy 1). |

---

## 3. Strategy Rules (Draft)

### Rule 0 — Pre-Strategy Filters (The "Gatekeeper")
When the strategy yields more signals than available capital slots, we use underlying health metrics to prioritize the highest quality stocks. 
*   **Status:** **Informational Only**. These do **not** act as a hard filter for the backtest engine but are used for UI/Reporting to tag "Gold/Silver" setups.
1.  **Long-term Trend:** `Close >= SMA(200)`.

### Rule 1 — The Squeeze Setup
- **Metric:** `Current Bandwidth <= Minimum(Bandwidth over last 60 days)`.
- **Status:** Armed. The strategy is now watching for a breakout.

### Rule 2 — The Breakout Trigger
Enter **Long at Close** when:
1.  **Armed:** The stock was in a "Squeeze" within the last 5 days. (Squeeze = 60-day bandwidth low).
2.  **Momentum:** `Close > bbUpper`.
3.  **Volume Confirmation:** `Volume > 2.0 * SMA(Volume, 20)` (Significant institutional force).

### Rule 3 — Exit Roadmap (The Three Phases)
The strategy uses the same **Three-Phase Roadmap** to manage risk and maximize profit using **Intraday GTT Exits**, ensuring we ride the volatility expansion for as long as possible.

| Phase | Activation Trigger | Stop Loss Rule (Intraday GTT) | User Story |
|-------|-------------------|--------------------------------|------------|
| **1. Safety** | **At Entry** | **Structural Low:** Lowest low of the 5-day setup window **plus** the entry day. | "Survive the initial breakout attempt without risking a massive reversal." |
| **2. Protection** | **Profit >= 2%** | **Break Even:** Entry Price. | "The breakout has momentum. I won't let this turn into a loser." |
| **3. Profit** | **Today's High > Entry Day's High** | **Staircase Trail:** Low of the **previous trading day**. | "Expansion confirmed. Ride the trend using yesterday's floor as a hard exit." |

*Note: Triggers are adapted for the Squeeze context. Phase 2 ensures capital protection early in the move, while Phase 3 activates as soon as the breakout creates a 'higher high' relative to the entry day.*


### Exit Priority Order
1.  **Stop Loss** (Phase 1, 2, or 3 logic depending on price progress).
2.  **Max Hold** (Optional cap; current default 999).

---

## 4. Backtest Simulation Engine Constraints
(Consistent with Strategy 1)
1. **Data Scope:** Uses **only daily candlestick data** (OHLC).
2. **Execution Timing:** Entry at Daily `Close` (simulating 3:20 PM).
3. **Exit Timing:** Intraday GTT simulation using Daily `Low` and `Open`.

---

## 6. Explainability & Debugging

The backtest engine will provide the following debug artifacts for every squeeze trade:

### A. Breakout Validation
- **Squeeze Check:** "Armed (60-day low bandwidth: 4.2% on 2026-04-12)."
- **Trigger Check:** "Entry on Upper Band Break: Close (152.0) > Upper Band (148.5) + Volume (2.5x SMA)."

### B. Exit Reasoning
- **Phase 1 (Safety):** Initial SL set at Structural Low (142.0).
- **Phase 2 (Protection):** "Moved to Break Even (152.0) on +2% profit (Price: 155.1)."
- **Phase 3 (Profit):** "Trail Activated: High (162.0) > Entry High (152.0). Trail SL set to Yesterday's Low."

### C. Verification Data
- A line-by-line snapshot of the trade period (OHLC, Bandwidth, SMA20, Upper/Lower Bands) will be attached to each trade result for auditing.

---
