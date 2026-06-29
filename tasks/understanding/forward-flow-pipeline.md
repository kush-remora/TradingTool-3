# The Forward Flow Pipeline (The Accumulation Watchlist)

**Core Philosophy:** *Quality over Quantity.*

**Objective:** Identify stocks that are actively forming a Wyckoff accumulation base (Phase B) or reaching supply exhaustion (Phase C) *before* they break out. This pipeline buys the trader **Time**—time to research fundamentals, wait for the perfect BHEL-style structural setup, and build maximum conviction for a 30%+ swing trade.

---

## Step 1: The Early Intake (The Chartink CSVs)

While the Backward Pipeline looks for breakouts, the Forward Pipeline looks for silence. Every day, the system ingests specific Chartink backtest CSVs (containing 9 months of historical hits):

**Phase B (Accumulation) CSVs:**
*   Large, Mid, Small Cap (Run *without* the equilibrium filter to catch all base shapes)
*   Large, Mid, Small Cap (Run *with* the equilibrium filter to flag perfectly flat, Grade A+ bases)

**Phase C (Exhaustion) CSVs:**
*   Large, Mid, Small Cap (The "Dead Pocket" where volume consistently stays below moving averages)
*   **The Extreme Anchor Scanners:** Dedicated Chartink scanners that flag the exact historical dates a stock printed its Lowest Volume in a Quarter (LVQ), 100 days (LV100), or Year (LVY).

---

## Step 2: Ingestion & The "Unbroken Chain" Clustering

When these Chartink CSVs are uploaded, the Kotlin backend acts as a time-series ledger. Because bases take months to build, a stock will trigger the scanners sporadically.

To calculate the true **Base Duration**, Kotlin uses the **Unbroken Chain** logic with a configurable gap tolerance (Default: **15 trading days**):
1. **Start from the most recent hit:** If a stock appears in today's CSV, it is flagged as active.
2. **Look backward:** Kotlin looks back up to 15 days to find the previous time it triggered.
3. **Chain and Repeat:** If it finds a hit (e.g., 10 days ago), it links them together and looks back *another* 15 days from that older date.
4. **The Break:** It keeps chaining backwards until it hits a 15-day "dead zone" where the scanner never fired. That dead zone marks the mathematical start of the base.

---

## Step 3: The "Quality" Filters (Chasing the BHEL Setup)

To enforce *Quality over Quantity*, we do not blindly buy every stock that compresses. Kotlin runs programmatic forensics on the Base Cluster to see if it matches the elite traits observed in the BHEL case study:

### A. Shape Detection (Polynomial Curve Fitting)
*   **The Check:** Kotlin runs a Quadratic Regression ($y = ax^2 + bx + c$) across the base window.
*   **The Goal:** Automatically reject Inverted U's (distribution). Mathematically confirm that the stock is in a flat equilibrium or an orderly downward drift (where buyers are patiently absorbing supply).

### B. The Equilibrium Floor (200-Day SMA)
*   **The Check:** Is the stock orbiting its 200-day moving average?
*   **The Goal:** Ensure the stock is not in a freefall. The 200-day average acts as the ultimate macro support level where institutions quietly build positions.

### C. The Delivery Absorption
*   **The Check:** Using the Simple Inverse Check (Volume Down, Delivery Up).
*   **The Goal:** Prove that on the exact days Chartink flagged a total volume dry-up, the *Absolute Delivery Quantity* remained high (specifically on red days), confirming institutional limit orders.

---

## Step 4: The Research Watchlist (The UI)

Stocks that survive the quality filters are presented on the **Forward Watchlist**. They are not ready to buy yet—they are ready to be researched.

**The Output View Data Model:**
1. **Identity:** Symbol & Market Cap Segment.
2. **Base Duration:** How many days the stock has been quietly compressing.
3. **The Institutional Footprint (The Nitin Ranjan Filter):** 
   * A count of volume spikes inside the base, ignoring all average noise.
   * `🟢 Green` (Up-day vol > 50 SMA)
   * `🔵 Blue` (Pocket Pivot: Up-day vol > highest down-day vol of last 10 days)
   * `🔴 Red` (Down-day vol > 50 SMA)
   * *A perfect conviction score looks like: 🟢 4 | 🔵 2 | 🔴 0*
4. **Extreme Dry-Up Tags (The Phase C anchors):**
   * Tags if the base contains historically dead days (e.g., `LVQ` - Lowest Volume in Quarter, `LV100` - Lowest Volume in 100 days, `LVY` - Lowest Volume in Year). This proves absolute supply exhaustion.
5. **The Forensics:** Shape (Flat/Drift) and 200-SMA proximity.

**The Human Element:**
The stock sits on this UI while you perform fundamental research (earnings, sector tailwinds, management). 

When the stock finally triggers a Phase D Ignition, it automatically jumps from this research list to your Execution list. Because you already vetted the fundamentals and watched the base form, you can execute the trade with zero hesitation and maximum size.
