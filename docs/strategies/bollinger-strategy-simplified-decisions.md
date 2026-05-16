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

---

## 3. Simplified Entry Rules (Decided)

Enter **long at close** when the setup is armed and **any one of the three confirmation triggers** fires, provided the stock is no longer "broken".

### Rule 1 — Recent lower-band stress (setup)
- Within the last **`signalWindowDays`** trading bars (default **5**), at least one bar has:
  - `close < bbLower`
- **Do not buy on the touch bar alone** — this only arms context (“we were in the buy zone recently”).
- **Speed of Entry:** The fastest possible entry is the **very next day** (Day + 1) after the touch if it is a Double Green day and triggers Rule 2. We do not artificially wait 2 or 3 days if the momentum is already there.

### Rule 2 — Hybrid Confirmation (Trigger)
To reduce the "late entry tax" while protecting against falling knives, we accept **any of the following** as a valid entry trigger on a **Double Green Day** (`close > close(yesterday)` AND `close > open`):

*   **Fast Entry A (Momentum Break):** `close(today) > 5-Day EMA`
    *   *User Story:* The downward momentum is so thoroughly broken by today's price action that waiting for tomorrow is unnecessary.
*   **Fast Entry B (Volume Spike):** `volume(today) > 2.0 * SMA(volume, 20)`
    *   *User Story:* Institutional money stepped in heavily to buy the bottom. The massive volume validates the turn.
*   **Safe Entry C (The Fallback):** `close(yesterday) > close(day-2)`
    *   *User Story:* Volume was normal and the bounce was small, but it managed to string together two consecutive green days. Slow, steady, and safe.

**Double Green Logic:** By requiring the price to be higher than both the previous close and the daily open, we avoid "fading gaps" where a stock opens high but sells off during the day. We only buy when buyers are in control from start to finish.

### Rule 3 — Not still broken
- `close(today) > bbLower(today)`
- **User Story:** Regardless of the trigger used in Rule 2, the entry bar must close **above** the lower Bollinger Band. We do not buy momentum or volume that is still trapped under the lower line.

### Rule 4 — Adaptive Risk (Volatility-Based)
- **Metric:** `Bandwidth Recovery = (EntryPrice - bbLower) / (bbUpper - bbLower)`
- **Threshold:** **0.75 (75%)**
- **User Story:** Instead of a fixed percentage, we use the stock's own volatility (the bands) to define a "Big Move". If the bounce has already covered 75% of the total bandwidth, we acknowledge the extreme momentum but protect our capital by switching to a tighter stop loss immediately.

### Execution Timing (Hybrid Approach)
To avoid intraday fake-outs while ensuring capital protection, the strategy uses a hybrid execution model:
- **Entries (Rule 2 & 3):** Evaluated and executed near the **End of Day (EOD, ~3:20 PM)**. We do not buy at 11:00 AM because an intraday signal might fade into a red candle by the close.
- **Phase 1 Hard Stop Loss:** Executed **Intraday**. If the price drops below the stop-loss level at any time during the day, we exit immediately (e.g., via a GTT order) to prevent a massive crash.
- **Trailing Stops (Phase 2 & 3):** Evaluated at the **End of Day (EOD)**. We give the stock intraday breathing room and only exit if the daily candle *closes* below the trailing level.

### Indicators (unchanged)
- Bollinger Bands: **BB(20, 2)**
- Volume MA: **SMA(20)**
- Fast MA: **EMA(5)**
- Timeframe: **daily**
- Position: **long-only**

---

## 4. Simplified Exit Direction (Decided)

Instead of a fixed percentage, the strategy uses a **Three-Phase Roadmap** based on the Bollinger Band levels to manage risk and maximize profit.

| Phase | Trigger | Stop Loss Rule | User Story |
|-------|---------|----------------|------------|
| **1. Safety** | At Entry | **Adaptive:**<br>• If Bandwidth Recovery < 0.75: **Structural Low** (lowest low of the 5-day setup window **plus** the entry day).<br>• If Bandwidth Recovery ≥ 0.75: **Yesterday's Low**. | "Survive the initial bounce without risking a massive loss on high-volatility moves." |
| **2. Protection** | Close > Middle Band (20 SMA) | **Break Even:** Entry Price. | "I won't let a winning trade turn into a loser. Playing with house money now." |
| **3. Profit** | High >= Upper Band | **Staircase Trail:** Low of the **previous trading day**. | "Target zone reached. Ride the trend until it breaks using yesterday's floor." |

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

## Changelog

| Date | Change |
|------|--------|
| 2026-05-17 | Initial decision log from strategy simplification discussion |
