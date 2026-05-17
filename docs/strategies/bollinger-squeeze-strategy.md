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
| **Setup** | In the recent setup window, detect any 3-day squeeze sequence where each day is near the 60-day lowest width using a tolerance buffer. |
| **Trend** | Trade only in the direction of the primary trend (e.g., above SMA 200). |
| **Trigger** | Phase-2 breakout: two consecutive closes above upper band + high volume. |
| **Simplicity** | Use Daily OHLC data only (consistent with Strategy 1). |

---

## 3. Strategy Rules (Draft)

### Rule 0 — Pre-Strategy Filters (The "Gatekeeper")
When the strategy yields more signals than available capital slots, we use underlying health metrics to prioritize the highest quality stocks. 
*   **Status:** **Informational Only**. These do **not** act as a hard filter for the backtest engine but are used for UI/Reporting to tag "Gold/Silver" setups.
1.  **Long-term Trend:** `Close >= SMA(200)`.

### Rule 1 — The Squeeze Setup (Phase 1: Tight Space Detection)
- For each day in the last `setupWindowDays`, check if that day and the previous 2 days are all squeeze days.
- A **squeeze day** means:  
  `todayWidth <= baselineWidth * (1 + tightSqueezeTolerancePct / 100)`  
  where `baselineWidth` is the 60-day minimum width on that day.
- Default `tightSqueezeTolerancePct = 12`.
- If **any** such 3-day sequence is found in the window, setup is **Armed**.
- **Status:** Armed. The strategy is now watching for a breakout.

**Why this is better:**
- We still keep strict squeeze definition.
- We avoid depending on only one recent marker.
- If 3 squeeze days happened earlier in the window, we keep setup ready until breakout comes.

### Rule 2 — The Breakout Trigger (Phase 2: Expansion Confirmation)
Enter **Long at Close** when:
1.  **Armed:** A valid 3-day squeeze sequence exists in the last `setupWindowDays`.
2.  **Momentum Trigger (Either path):**
    - **Fast Day-1 Entry (no need to wait for day 2):**
      - `Close(today) > bbUpper(today)`
      - `Close(today) vs Close(yesterday) >= 8%`
      - `Volume(today) / SMA20(Volume, excluding today) >= 10x`
    - **OR Standard 2-Day Confirmation:**
      - `Close(today) > bbUpper(today)`
      - `Close(yesterday) > bbUpper(yesterday)`
3.  **Green Candle Confirmation for standard 2-day path:**
      - `Close(today) > Open(today)`
      - `Close(yesterday) > Open(yesterday)`
4.  **No volume check is required for this standard 2-day path.**
5.  **RSI Heat Guard (applies to all entries):**
      - If RSI(14) is greater than 68 on **today or yesterday**, do not enter.

### Rule 3 — Exit Roadmap (The Three Phases)
The strategy uses the same **Three-Phase Roadmap** to manage risk and maximize profit using **Intraday GTT Exits**, ensuring we ride the volatility expansion for as long as possible.

| Phase | Activation Trigger | Stop Loss Rule (Intraday GTT) | User Story |
|-------|-------------------|--------------------------------|------------|
| **1. Safety** | **At Entry** | **Structural Low:** Lowest low of the 5-day setup window **plus** the entry day (Total 6-day lookback). | "Survive the initial breakout attempt without risking a massive reversal." |
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
- **Squeeze Check:** "Armed (3-day squeeze sequence found in setup window; latest sequence ended on 2026-04-12)."
- **Trigger Check (Fast Day-1):** "close > upper band, close-vs-prev-close >= 8%, and volume-vs-prev20avg >= 10x."
- **Trigger Check (Standard):** "2-day upper-band breakout confirmed (prev close > prev upper and today close > today upper)."

### B. Exit Reasoning
- **Phase 1 (Safety):** Initial SL set at Structural Low (142.0).
- **Phase 2 (Protection):** "Moved to Break Even (152.0) on +2% profit (Price: 155.1)."
- **Phase 3 (Profit):** "Trail Activated: High (162.0) > Entry High (152.0). Trail SL set to Yesterday's Low."

### C. Verification Data
- A line-by-line snapshot of the trade period (OHLC, Bandwidth, SMA20, Upper/Lower Bands) will be attached to each trade result for auditing.

---
