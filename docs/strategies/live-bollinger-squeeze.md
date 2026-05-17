# Live Market: Bollinger Squeeze Strategy

**Status:** Draft - Active Development
**Base Indicator:** Bollinger Bands (20, 2)

---

## 1. Phase 1: Post-Market Setup Screener

**Execution Time:** Daily, after market close.
**Goal:** Identify stocks where volatility has contracted to an extreme low, indicating a potential future breakout.

### 1.1 The Core Rule (Filter 1: The Squeeze)
This is a single, simple mathematical rule to find compressed price action.

*   **Lookback Period:** 60 trading days.
*   **Condition:** We look for **3 consecutive days** where the Bollinger Bandwidth is exceptionally tight.
*   **"Tight" Definition:** On a given day, the Bandwidth must be less than or equal to the lowest Bandwidth recorded in the entire 60-day window, plus a 12% tolerance buffer.
*   **Result:** If this 3-day sequence is found anywhere within the 60-day window, **Filter 1 is Passed**.

### 1.2 Output Columns
The daily screener report will output the following columns to help prioritize stocks for the next day:

| Column Name | Description |
| :--- | :--- |
| **Symbol** | The stock ticker symbol. |
| **LTP** | Last Traded Price (Today's Close). |
| **Alert Status** | **Crucial:** Displays the current readiness state (e.g., "Day 1 Alert", "Squeezing (Green)", "Triggered Today"). |
| **Above 200 SMA** | Yes/No. Informational gatekeeper to show if the long-term trend is upward. |
| **Filter 1 (Squeeze)** | **Origin Date:** The first day the 3-day squeeze was valid. <br> **Latest Date:** The most recent day the squeeze was valid. |
| **Filter 2 (Breakout)** | **Origin Date:** The first day the breakout trigger (Path A or B) occurred after the squeeze. <br> **Latest Date:** The most recent day a breakout trigger occurred. |
| **Filter 2 Type** | Displays "Fast 1-Day", "Standard 2-Day", or "None" for the **Origin** trigger. |
| **Current RSI** | Today's RSI(14) value. |
| **Trigger RSI** | The RSI(14) value on the specific `Filter 2 Origin Date`. |
| **52W Max RSI** | The highest RSI(14) value recorded for this stock in the last 52 weeks. |

### 1.3 Breakout Triggers (Filter 2 Conditions)
A stock is considered to have "broken out" (Filter 2 = Passed) if it meets **either** of the following two paths:

*   **Path A: Standard 2-Day Confirmation**
    *   `Close(today) > bbMiddle(today)` AND `Close(yesterday) > bbMiddle(yesterday)`
    *   Both days must be green candles: `Close > Open` for both days.
*   **Path B: Fast Day-1 Entry** (Explosive momentum)
    *   `Close(today) > bbUpper(today)`
    *   `Close(today) vs Close(yesterday) >= 8%`
    *   `Volume(today) / SMA20(Volume, excluding today) >= 10x`

### 1.4 Pre-Breakout Alerting Logic (The "Readiness" System)
To prevent missing trades and to prepare for execution, the **Alert Status** column translates the raw data into actionable warnings based on the following hierarchy:

1.  **"Day 1 Alert" (High Priority):** 
    *   *Condition:* Filter 1 is passed. Today's Close > Middle Bollinger Band (20 SMA) AND Today's Close > Open (Green Candle). 
    *   *Meaning:* The first half of "Path A" has just occurred. **User Action:** Be ready tomorrow; if a second green close above the middle band happens, execute the trade.
2.  **"Squeezing (Green)":** 
    *   *Condition:* Filter 1 is passed. Today is a Green Candle, but it has not closed above the Upper Band yet.
    *   *Meaning:* The stock is compressed and showing upward momentum. **User Action:** Monitor closely, a breakout could happen any day.
3.  **"Active Squeeze":**
    *   *Condition:* Filter 1 is passed. Today is a flat or red candle.
    *   *Meaning:* The setup is armed but resting.
4.  **"Triggered Today":**
    *   *Condition:* Filter 2 passed *today*.
5.  **"Stale / Used":**
    *   *Condition:* Filter 2 passed in the past (Filter 2 Date > Filter 1 Date).

### 1.5 Manual RSI Heat Guard
Instead of using a hardcoded filter (like rejecting entries if RSI > 68), the screener provides contextual RSI data for manual visual inspection.
*   **User Story:** By comparing the `Trigger RSI` against the historical `52W Max RSI`, you can determine if a stock is genuinely "overheated" for its specific personality. If a stock routinely hits 85 RSI on breakouts, a Trigger RSI of 70 is not overheated. If its 52-week max is 72, a Trigger RSI of 70 is extremely overheated.

### 1.6 Handling Stale Setups
By displaying both the `Filter 1 Date` (Squeeze) and the `Filter 2 Date` (Breakout), the user can manually filter out stale setups.
*   **Ideal Setup:** `Filter 1 Passed` is Yes, and `Filter 2 Passed` is No (or the `Filter 2 Date` is very old, before the current squeeze).
*   **Stale Setup:** If the `Filter 2 Date` is more recent than the `Filter 1 Date`, the stock has already broken out of that specific squeeze and should likely be ignored for a new entry.

---

## 2. Phase 2: Live Execution Rules

While the backtest assumes a rigid 3:20 PM entry, live trading requires handling real-time data and manual timing decisions.

### 2.1 Handling Intraday Data (LTP Substitution)
The screener can be run at any point during market hours. To provide a "live preview" of the strategy status:
*   For all current-day calculations, the screener must substitute the real-time **Last Traded Price (LTP)** as the `Close(today)` variable.
*   *Implication:* If run at 11:00 AM, an "Alert Status" of "Day 1 Alert" means the condition is met *at that exact moment*. The user must understand this is dynamic and could change before the market closes.

### 2.2 Execution Timing Framework
When a stock triggers a breakout (e.g., transitioning from "Day 1 Alert" to a Day 2 confirmation), the user must decide exactly *when* to execute the buy order.

**A. The Default Baseline: 3:15 PM Execution (Safe)**
*   **Action:** Execute the trade near the end of the day, between 3:15 PM and 3:25 PM.
*   **Why:** The strategy relies on daily closes to avoid intraday "fakeouts" (where price spikes above the band in the morning but crashes by the afternoon, leaving a large wick). Waiting near the close confirms the momentum is real.

**B. The Intraday Override: The Momentum Exception (Aggressive)**
*   **Action:** Execute the trade earlier in the day (e.g., 11:00 AM or 1:00 PM).
*   **Condition:** Only permitted if the intraday price action is undeniably strong.
    1.  Price is significantly above the Upper Bollinger Band.
    2.  Intraday volume is already massive (e.g., trading >100% of average daily volume by mid-day).
    3.  The candle structure is strong and full-bodied (no large upper wicks indicating selling pressure).

---

## 3. Phase 3: Active Trade Tracker (Exit Management)

To make exit management clean and mechanical, we use a secondary dashboard (The Tracker) specifically for active positions. You log your entry, and the system dynamically calculates your required Stop Loss (GTT) every day based on the Three-Phase Roadmap.

### 3.1 The Tracker Columns
This screen is checked daily after market close to tell you exactly how to update your broker's GTT orders for the next day. It also provides live context to help you make discretionary exit decisions if the stock becomes overheated.

| Column Name | Description |
| :--- | :--- |
| **Symbol** | The stock ticker symbol. |
| **Buy Date** | (Manual Input) The date you executed the trade. |
| **Buy Price** | (Manual Input) Your exact average entry price. |
| **LTP** | Last Traded Price (Today's Close / Live Price). |
| **Current Profit %** | Calculated: `((LTP - Buy Price) / Buy Price) * 100`. |
| **Current Phase** | Displays the active phase: "1. Safety", "2. Protection", or "3. Profit". |
| **Required SL (GTT)** | **Crucial:** The exact price you should set for your GTT Stop Loss in your broker today. |
| **Today RSI** | Current RSI(14) value. Useful for identifying exhaustion. |
| **Max 1Y RSI** | The highest RSI(14) value recorded in the last 52 weeks. |
| **Upper BB Price** | The current value of the Upper Bollinger Band. |
| **Middle BB Price** | The current value of the Middle Bollinger Band (20 SMA). |

### 3.2 The Automated SL Logic (The 3-Phase Roadmap)
The tracker automatically determines the `Current Phase` and calculates the `Required SL (GTT)` using these progressive rules. The system evaluates these in order, locking in the highest possible phase.

**Phase 1: Safety (Initial Setup)**
*   **Condition:** Active immediately upon entering the trade.
*   **SL Calculation:** The lowest `Low` of the 5 days prior to (and including) the `Buy Date`. 
*   **Goal:** Survive the initial breakout without risking a massive reversal.

**Phase 2: Protection (Break Even)**
*   **Condition:** Activates when `Current Profit %` $\ge$ 2%.
*   **SL Calculation:** Your exact `Buy Price`.
*   **Goal:** The trade is working. Move to house money. Never let a 2% winner turn into a loser.

**Phase 3: Profit (Staircase Trail)**
*   **Condition:** Activates when the stock makes a new high (Any daily High > The High of the `Buy Date`).
*   **SL Calculation:** The `Low` of the *previous* trading day. (The system updates this SL dynamically every single day as new daily candles form).
*   **Goal:** Ride the trend. Use yesterday's floor as a hard exit to lock in profits when the trend finally breaks.

---

## 4. Phase 4: Telegram Alerting Integration

To minimize screen time and ensure critical actions are never missed, the strategy integrates with Telegram to push high-signal, low-noise alerts directly to mobile.

### 4.1 Alert Triggers & Formats

**1. The "Get Ready" Alert (Setup Warning)**
*   **Trigger:** Any stock transitions to **"Day 1 Alert"** status.
*   **When it fires:** When the scanner is run manually (intraday or post-market) and detects the condition.
*   **Message Format:** `🔔 SQUEEZE ALERT: [SYMBOL] is on Day 1 Breakout. Green candle above Upper BB. Be ready to execute tomorrow.`

**2. The "Execution" Alert (Action Required)**
*   **Trigger:** Any stock transitions to **"Triggered Today"** (Filter 2 Passed).
*   **When it fires:** Crucial for intraday scanner runs to catch aggressive Volume/Momentum triggers, or post-market for standard Day 2 triggers.
*   **Message Format:** `🚀 BREAKOUT TRIGGERED: [SYMBOL] has passed Filter 2. Evaluate for immediate entry.`

**3. The "Daily Chores" Digest (Exit Management)**
*   **Trigger:** Automated daily push after market close (e.g., 4:00 PM).
*   **When it fires:** Scans the **Active Trade Tracker** for all open positions.
*   **Message Format:** A combined summary instructing you how to update your broker GTTs.
    ```text
    📋 EOD GTT Update Required:
    [SYMBOL 1] - Phase 2. Move GTT to Break Even: ₹[Price]
    [SYMBOL 2] - Phase 3. Move GTT to Yesterday's Low: ₹[Price]
    ```

---
*End of Strategy Document.*