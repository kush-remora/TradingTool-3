# The Backward Flow Pipeline (Phase D Forensics)

**Objective:** Catch stocks that are experiencing active institutional markup (Phase D) *today*, and programmatically work backward to prove they launched from a valid, high-quality accumulation base (Phase B).
I th
**Primary Trade-off:** Because we discover these stocks exactly as they are breaking out, time for fundamental research is limited. This pipeline acts as a safety net for high-conviction setups that we missed during the Forward Flow.

---

## Step 1: The Phase D Intake (The 4-Part Net)

We cast a wide net using four independent sources to ensure no footprint is missed.

1. **Groww Volume Shockers:** Upload the daily top 100 stocks experiencing maximum volume surges.
2. **Local Delivery Anomalies:** Run the native Kotlin daily check for stealth footprints (Absolute shock and Absorption shock).
3. **Chartink Ignition Scanner (1 CSV):** Upload the 9-month historical backtest flagging the exact day of markup.
4. **Chartink Momentum Scanner (1 CSV):** Upload the 9-month historical backtest confirming macro trend guardrails.

---

## Step 2: The Garbage Clearing Gauntlet (For Sources 1 & 2)

Sources 3 and 4 (Chartink) already handle their own structural garbage clearing mathematically. 
However, raw triggers from Groww (1) and Delivery (2) must survive a strict gauntlet to remove noise:
*   **The Extension Filter (No FOMO):** Yesterday's close cannot be overextended from the 50 DMA.
*   **The Green Check (No Distribution):** Today's Close >= Today's Open.
*   **The No-Rejection Check (No Wicks):** Today's Close must be pinned in the top 25% of the daily price range.

*Note: Sources 3 and 4 do NOT intersect with this gauntlet, they bypass it directly to the grading step.*

---

## Step 3: The Phase D Candidate List (Raw Convergence View)

This is the convergence phase. Because these footprints rarely happen on the same day, the system looks at a **rolling 5-day window** to build the Phase D Candidate List. 

The typical sequence of a breakout unfolds over a few days: 
**`Ignition` $\rightarrow$ `Delivery Silent Shock` $\rightarrow$ `Momentum` $\leftrightarrow$ `Volume Shocker`** *(The last two can happen in any order).*

**Phase 1 Approach (Raw Data View):**
Rather than prematurely guessing a complex grading system, the system will initially just present the raw convergence data. 
For every stock in the candidate list, the UI will simply display:
*   **Source Tags:** Which sources it appeared in (Groww, Delivery, Ignition, Momentum).
*   **Event Timeline:** The exact date or "days ago" it was last seen in each of those sources.

This allows you to visually see the timeline (e.g., "Ignition 3 days ago, Delivery yesterday, Groww today") and build human intuition before we hardcode a mechanical scoring system.

*(TODO: Design a programmatic grading system once sufficient live data and patterns have been observed).*

---

## Step 4: The Backward Validation (The Phase B Intersection & Forensics)

Before running heavy backend calculations, we leverage Chartink to find where the accumulation mathematically occurred.

### A. The Phase B Intersection (The 6 CSVs)
Every day, you will upload **6 Phase B (Accumulation) Chartink CSVs** (containing 9 months of historical data):
*   3 CSVs (Large/Mid/Small Cap) run *without* the equilibrium rule to catch all shapes.
*   3 CSVs (Large/Mid/Small Cap) run *with* the equilibrium rule to flag perfectly flat Grade A+ bases.

The Kotlin backend takes the Phase D Candidate list and **intersects** it with these 6 CSVs. If a candidate appears in these Phase B files, the backend immediately knows the exact historical dates when the base compressed.

### B. Shape Classification (Polynomial Curve Fitting)
Only on those specific stocks (and their specific historical dates), Kotlin fetches the raw EOD data and runs a Quadratic Regression ($y = ax^2 + bx + c$).
*   **The Action:** Chartink cannot see direction, so Kotlin mathematically rejects Inverted U's (distribution). It confirms the Chartink footprint is a valid flat, downward drift, or upward drift accumulation shape.

### C. Phase C Delivery Validation (The Simple Inverse Check)
We do not force Kotlin to calculate massive historical baselines. Chartink has already mathematically isolated the exact days where total volume collapsed (e.g., `volume < 5-day SMA`). 

Kotlin simply looks at those exact dates and applies the inverse logic:
1. **The Inverse Spike:** As total trading volume crashed, did the *Absolute Delivery Quantity (DQ)* remain steady or spike? (This proves institutions were absorbing the remaining float).
2. **The Red Day Divergence:** On the days inside that dry-up cluster where the stock closed RED (price down), did the Delivery Quantity increase? (This proves limit-order buying on the dip).

---

## Step 5: The Final Output View

The system outputs a raw convergence UI to aid fast decision-making:
*   **Identity:** Symbol, Market Cap Segment.
*   **The Backward Timeline:** Groww (Days Ago), Delivery (Days Ago), Ignition (Days Ago), Momentum (Days Ago).
*   **Base Forensics:** Shape (Flat/Drift), Base Duration, Delivery Divergence confirmed (Yes/No).
*   **Today's Action:** Close Price, Volume Surge multiplier, Distance to Base Breakout level.

---

## The Core Strategy: Decision Fatigue & High Conviction

While mathematically it seems appealing to run 100 trades at 5%, the human reality of trading dictates the exact opposite. 

**The Decision Constraint:**
To execute 100 trades a year, you must make 100 perfect decisions. Every single decision carries the risk of cognitive fatigue, emotional mistakes, and execution errors. Furthermore, transaction costs, slippage, and taxes aggressively eat into high-frequency, low-margin trading.

**The Pipeline Alignment (Fewer Decisions, Bigger Swings):**
The true goal of this software is to enable **Scenario 4 (15 to 20 trades a year aiming for 30%+ returns)**. 
By drastically reducing the number of trades, you preserve mental capital. You only execute when the system flags an absolute Grade A+ Wyckoff setup with perfect institutional footprints. 

Because we are aiming for 30% swings, we must hold the stock for longer periods (weeks/months). To have the psychological conviction to hold a stock that long without getting shaken out, the trader *must* have time to deeply research the company's fundamentals before entering. This makes the **Forward Flow (Phase B Watchlist)** the most critical part of the entire system, as it provides the time required to build that conviction.
