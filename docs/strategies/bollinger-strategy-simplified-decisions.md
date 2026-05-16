# Bollinger Strategy — Simplified Design Decisions

**Status:** Decision log (discussion finalized through 2026-05-17)  
**Implements in code:** Not yet — see [bollinger-backtest-source-of-truth.md](./bollinger-backtest-source-of-truth.md) for current backtest behavior.

This document captures product and rule decisions so they are not lost. Update it when we change direction.

---

## 1. Origin & User Story

### Base idea
- **Buy** when price is unusually low vs Bollinger Bands (at / below lower band).
- **Sell** when price is unusually high (at / above upper band).
- Belief: price tends to revert toward the middle band after extreme moves.

### Problems observed on charts
| Problem | Description |
|---------|-------------|
| **Knife** | Lower-band touch does not mean the turn is now; price can fall another 5–10%. |
| **Fake bounce** | One up day after a touch, then price rolls over again. |
| **Upper-band uncertainty** | Above upper band can mean top or continuation — less of a focus for now. |

Knife and fake bounce are treated as **the same entry problem** for this strategy.

### Complexity trap
- Extra rules (RSI windows, mirrored exits) were added to handle edge cases.
- Too many rules feel like **fitting the backtest** and are hard to trust.
- Goal: **keep Bollinger as the main story**, with **minimal** extra logic.

---

## 2. Design Principles (Decided)

| Principle | Decision |
|-----------|------------|
| **Priority** | **Entry quality** matters most. |
| **Frequency** | Prefer **fewer, higher-trust** trades over churn. (**Purity > frequency.**) |
| **Entry timing** | OK to enter **later** (e.g. after two green days) even if some upside is missed. |
| **Exit** | **Simple exits are fine** — ~**5% take profit** is acceptable; **selling early** (missing a larger run) is acceptable. |
| **Overfitting** | Avoid stacking many conditional gates; each rule must have a clear user story. |

### Explicitly not pursuing (for now)
- Early entry at band touch + high churn + ATR stop as the primary model.
- Symmetric “reversal exit” (upper band + RSI + two red days) — **intended to drop** when we implement simplification.

### Backtest Simulation Engine Constraints
To ensure absolute clarity that this document dictates backtest engine behavior without introducing infrastructure complexity, the backtest adheres strictly to the following constraints:
1. **Data Scope:** The engine uses **only daily candlestick data** (High, Low, Open, Close).
2. **No Intraday Polling:** We do **not** fetch or process 5-minute or 15-minute candlesticks.
3. **EOD Entry Proxy:** The daily `Close` price is used mathematically to simulate the 3:20 PM live execution.
4. **Intraday GTT Simulation:** Intraday stop losses (GTTs) are simulated by checking if the calculated stop level falls within the daily candle's range (`Low <= Stop Level`). The exit is mathematically recorded at the stop price (or `Open` in the event of a gap-down), simulating an intraday exit perfectly using EOD data.

---

## 3. Simplified Entry Rules (Decided)

Enter **long at close** when the setup is armed and **any one of the three confirmation triggers** fires, provided the stock is no longer "broken".

### Rule 0 — Pre-Strategy Filters (The "Gatekeeper")
When the strategy yields more signals than available capital slots, we use underlying health metrics to prioritize the highest quality stocks. 
*   **Status:** **Informational Only**. These are evaluated *prior* to the Bollinger dip but do **not** act as a hard filter for the backtest engine. They are exposed in the reporting/UI layer to allow the user to manually select the best candidates (e.g., "Silver/Gold" level stocks).
1.  **Trend Alignment:** `Close >= SMA(200)`
2.  **Relative Strength (Alpha):** `RSI(14) > 50`
3.  **Positive Momentum:** `ROC(60) > 0`

### Rule 1 — Recent lower-band stress (setup)
- Within the last **`signalWindowDays`** trading bars (default **5**), at least one bar has:
  - `close < bbLower`
- **Do not buy on the touch bar alone** — this only arms context (“we were in the buy zone recently”).
- **Speed of Entry:** The fastest possible entry is the **very next day** (Day + 1) after the touch if it is a Double Green day and triggers Rule 2. We do not artificially wait 2 or 3 days if the momentum is already there.

### Rule 2 — Hybrid Confirmation (Trigger)
To reduce the "late entry tax" while protecting against falling knives, we accept **any of the following** as a valid entry trigger on a **Double Green Day** (`close > close(yesterday)` AND `close > open`):

*   **Fast Entry A (Momentum Break):** `close(today) > 5-Day EMA`
*   **Fast Entry B (Volume Spike):** `volume(today) > 2.0 * SMA(volume, 20)`
*   **Safe Entry C (The Fallback):** `close(yesterday) > close(day-2)`

**Double Green Logic:** By requiring the price to be higher than both the previous close and the daily open, we avoid "fading gaps" where a stock opens high but sells off during the day. We only buy when buyers are in control from start to finish.

### Rule 3 — Not still broken
- `close(today) > bbLower(today)`
- **User Story:** Regardless of the trigger used in Rule 2, the entry bar must close **above** the lower Bollinger Band. We do not buy momentum or volume that is still trapped under the lower line.

### Rule 4 — Adaptive Risk (Volatility-Based)
- **Metric:** `Bandwidth Recovery = (EntryPrice - bbLower) / (bbUpper - bbLower)`
- **Threshold:** **0.75 (75%)**
- **User Story:** Instead of a fixed percentage, we use the stock's own volatility to define a "Big Move". If the bounce has already covered 75% of the total bandwidth on the entry day, we immediately switch to **Yesterday's Low** as our stop loss to protect against a "blow-off top" reversal.

### Execution Timing (Intraday Exit Focus)
To avoid intraday fake-outs on entries while ensuring maximum capital protection on exits:
- **Entries (Rule 2 & 3):** Evaluated and executed near the **End of Day (EOD, ~3:20 PM)**. We do not buy at 11:00 AM because an intraday signal might fade into a red candle by the close.
- **Stop Losses (All Phases):** Executed **Intraday (GTT)**. If the price hits the defined stop level (Safety, Break-even, or Trail) at any point during market hours, the trade is closed immediately to prevent slippage and lock in gains.

### Indicators (unchanged)
- Bollinger Bands: **BB(20, 2)**
- Volume MA: **SMA(20)**
- Fast MA: **EMA(5)**
- Timeframe: **daily**
- Position: **long-only**

---

## 4. Simplified Exit Direction (Decided)

The strategy uses a **Three-Phase Roadmap** to manage risk and maximize profit using **Intraday GTT Exits**.

| Phase | Activation Trigger | Stop Loss Rule (Intraday GTT) | User Story |
|-------|-------------------|--------------------------------|------------|
| **1. Safety** | **At Entry** | **Structural Low:** Lowest low of the 5-day setup window **plus** the entry day.<br>*Exception: If Rule 4 (75% recovery) fires, use **Yesterday's Low**.* | "Survive the initial bounce without risking a massive loss on high-volatility moves." |
| **2. Protection** | **Close > Middle Band (20 SMA)** | **Break Even:** Entry Price. | "I won't let a winning trade turn into a loser. Playing with house money now." |
| **3. Profit** | **High >= Upper Band** | **Staircase Trail:** Low of the **previous trading day**. | "Target zone reached. Ride the trend until it breaks using yesterday's floor as a hard exit." |

### Exit Priority Order
1.  **Stop Loss** (Phase 1, 2, or 3 logic depending on price progress).
2.  **Max Hold** (Optional cap; current default 999).
3.  **Reversal Exit** (Removed - handled by the tight trail in Phase 3).

### Handling Failed Bounces
If a stock bounces off the lower band but lacks the strength to reach the upper band (e.g. rejects at the middle band), Phase 2 ensures we exit at **Break Even** rather than letting it fall back to the initial structural stop.

---

## 5. RSI (Decided)

RSI was in the **current** backtest (setup: RSI ≤ 30 in window; trigger: RSI rising).

| Topic | Status |
|-------|--------|
| Remove RSI from entry | **Yes** — two green days + band rules replace momentum confirmation. |
| RSI in UI/Debug | Keep values visible for audit, but logic is **ignored**. |

The removal of RSI simplifies the code and focuses the strategy on **price behavior** (rules 2–3).

---

## 6. Worked Example — Adani Green (Jan 2026)

Data slice used in discussion (24–26 Jan: **market closed**).

| Date | LTP | Lower BB | Rule 1 (stress in window) | Rule 2 (Hybrid Trigger) | Rule 3 (above lower BB) | Buy? |
|------|-----|----------|---------------------------|-------------------------|-------------------------|------|
| 2026-01-23 | 772.80 | 826.92 | Stress today | No | No | **No** — setup only |
| 2026-01-27 | 799.10 | 801.55 | Yes (23 Jan) | No (23 red vs 22) | No (799 < 801.55) | **No** |
| 2026-01-28 | 822.90 | 784.91 | Yes | **Yes (Safe Entry C: 2 greens)** | Yes | **Yes @ 822.90** |
| 2026-01-29 | 858.80 | 776.76 | Yes | Yes | Yes | Already in from 28 |

**Trading-day chain across holiday:**

```text
22 Jan  904.30
23 Jan  772.80   ← lower-band stress (knife)
27 Jan  799.10   ↑ vs 23, still below lower BB
28 Jan  822.90   ↑ vs 27, above lower BB → ENTRY
```

---

## 7. Comparison vs Current Backtest

| Area | Current ([source of truth](./bollinger-backtest-source-of-truth.md)) | Simplified (this doc) |
|------|----------------------------------------------------------------------|------------------------|
| Setup | Lower band **and** RSI ≤ 30 in window | Lower band only |
| Trigger | 2 greens + above lower BB + **RSI rising** | **Hybrid:** (EMA5 break OR Volume spike OR 2 greens) + above lower BB |
| Exit | SL → TP → reversal exit → max hold | **3-Phase:** Structural SL → Break-even → Staircase Trail |
| Philosophy | More gates | Fewer gates, entry purity, trend capture |

---

## 8. Next Steps (When Implementing)

1. Confirm **RSI removal** (yes/no).
2. Update backtest engine to match §3 and §4.
3. Replace or amend [bollinger-backtest-source-of-truth.md](./bollinger-backtest-source-of-truth.md) once code matches.
4. Re-run backtest on a small watchlist and compare trade count vs current.

---

## 9. Explainability & Debugging

To verify that the backtest is using actual data and applying logic correctly, the engine must produce a **Reasoning Log** for every trade. This allows the user to audit the "Why" behind every entry and exit.

### A. Entry Reasoning (Example)
- **Status:** `GOLD Setup` (Rule 0: SMA200=Yes, RSI50=Yes)
- **Setup:** `Armed` (Lower band touched 3 days ago: 2026-01-23)
- **Trigger:** `Safe Entry C` (Confirmed by 2 green days: 822.90 > 799.10 > 772.80)
- **Stop Loss Initial:** `772.80` (Structural Low of setup window)

### B. Exit Reasoning & Phase Transitions
The log must show exactly when the stop loss moved and why:
- **Phase 1 -> 2:** "Moved SL to 822.90 (Break Even) because Close (845.00) > Middle Band (840.10)."
- **Phase 2 -> 3:** "Activated Staircase Trail because High (910.00) >= Upper Band (905.00)."
- **Final Exit:** "GTT Hit: Intraday Low (890.00) <= Trail SL (895.00). Exit recorded at 895.00."

### C. Data Snapshot
Every trade must include a daily table of OHLC + BB values for the duration of the trade to allow manual recalculation/verification of indicators.

---

## Changelog

| Date | Change |
|------|--------|
| 2026-05-17 | Initial decision log from strategy simplification discussion |
