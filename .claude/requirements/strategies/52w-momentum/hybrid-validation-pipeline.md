# The Hybrid Wyckoff Validation Pipeline

## 1. Architectural Overview
This system uses a hybrid approach to validate Wyckoff Accumulation before taking trades on Momentum setups (like 52-week highs). 
It leverages the speed and experimentation flexibility of Chartink for broad mathematical filtering, and the programmatic power of a Kotlin backend for advanced structural analysis and human-bias removal.

The pipeline is split into two physical environments:
1. **The Discovery Engine (Chartink):** A web-based screener running off-the-shelf math rules to narrow 5,000 stocks down to a handful.
2. **The Validation Engine (Kotlin Backend):** A custom backend script that ingests the Chartink CSV, fetches raw market data via Kite, and runs advanced logic that off-the-shelf screeners cannot handle (like Shape Classification).

## 2. Step 1: The Chartink Stratification (The Universe)
Because Chartink cannot dynamically change math rules based on the size of a company, we cannot run a single scan for the entire market. Instead, the universe is restricted to the **Nifty 500**, broken into three separate Chartink screeners:

1. **The Large Cap Screener (Nifty 100):** Uses the tightest thresholds (e.g., `< 3.5%` volatility, `10%` DMA band).
2. **The Mid Cap Screener (Nifty Midcap 150):** Uses moderate thresholds (e.g., `< 4.5%` volatility, `15%` DMA band).
3. **The Small Cap Screener (Nifty Smallcap 250):** Uses the loosest thresholds to forgive higher baseline volatility (e.g., `< 6%` volatility, `20%` DMA band).

**The Output:** You run all three screeners at the end of the day, download the three resulting CSV files, and combine them. This combined list represents every stock in the Nifty 500 that currently exhibits the mathematical footprint of Wyckoff Accumulation.

## 3. Step 2: The Two-Pass Quality Classification (Handling Rule 3)
Because Wyckoff Accumulation has four valid shapes (Flat, Downward Drift, Upward Drift, Cup), we must run a two-pass classification system to prevent filtering out valid setups while still identifying the "Grade A+" ideal candidates.

**The Bias in Rule 3 (Balance of Power):**
Rule 3 demands that the number of Up Days and Down Days are almost perfectly equal (a difference of `<= 4`). This mathematically isolates perfectly **Flat Bases** and perfectly symmetrical **Cups**. However, it actively destroys valid **Upward Drifts** or **Downward Drifts** (which naturally have a heavily skewed ratio of green-to-red days).

**The Two-Pass Solution:**
To catch all valid setups while highlighting the best ones, you run the Chartink screeners twice:
1. **Pass 1 (The Wide Net):** Run the Chartink screener *without* Rule 3. Any stock on this list has a mathematically valid 30-day accumulation floor (catching all 4 shapes).
2. **Pass 2 (The Equilibrium Filter):** Run the Chartink screener *with* Rule 3. Any stock that passes this second list is tagged as a **Grade A+** setup. The Composite Man has established such absolute, deadlocked equilibrium that the price has formed a perfectly flat floor (the ideal candidate for the tightest stop-loss).

## 4. Step 3: The Temporal Evaluation (Handling Multiple Validation Dates)
When you evaluate a stock using these rules (either via Chartink backtests or natively in your Kotlin backend), you will often find that a stock satisfies the rules on multiple different dates throughout the year. For example, it might pass on March 10, March 15, and March 17, and then not pass again until June 10.

**The Philosophy:**
We cannot blindly group a March date and a June date into a single massive "4-month accumulation phase." Accumulation happens in discrete periods. The cluster of days in March represents one distinct accumulation base. The stock then likely moved (up or down), and the June date represents an entirely new re-accumulation base. 

**The Pipeline Action:**
When the Kotlin backend ingests this data, it should not try to artificially stitch distant days together. Instead, it must accurately **record every single validation date as an independent event**. 
*   If you see a tight cluster (March 10, 15, 17), it confirms a strong, active base is currently being built.
*   If you see multiple distinct periods over a year (March, then June, then August), it proves the stock is going through healthy "Step-Up Re-Accumulation" phases on its way to the 52-week high.
By recording the exact dates correctly, the backend provides an honest, bias-free history of exactly when the Composite Man stepped in to absorb supply.

## 5. Step 4: Data Ingestion and Base Clustering (The 9-Month Backtest)
The typical input into the Kotlin backend will be a **9-month historical CSV** exported from Chartink. This single file serves two completely different purposes:
1. **Live Trading:** Identifying "Recent Accumulation" (e.g., passing in the last 1-2 weeks) to take active trades today.
2. **Strategy Validation:** Identifying "Historical Accumulation" (e.g., passing 6 months ago) to backtest if the math actually resulted in a successful Phase D markup.

**The Pipeline Action (How to read the CSV):**
When the backend reads the CSV, it will see multiple rows for the same stock. As seen in the BHEL case study, a single base will generate multiple passing dates (e.g., March 10 at ₹260, and March 15 at ₹250). 
The backend must run a **Clustering Algorithm**:
*   **Group by Proximity:** If dates are close enough together (e.g., within a 2-3 week window), they are grouped into a single `Base Cluster`.
*   **Record the Variance:** Within that cluster, the only real distinction is the price. The system records the Price Range of the cluster (e.g., Base formed between ₹250 - ₹260).
*   **Detect New Bases:** If a validation date appears with a large time gap (e.g., 3 months later), it is mathematically declared a brand new `Base Cluster`.

## 6. Step 5: The Shape Classifier (Filtering out the Inverted U)
Because Chartink's mathematical compression rules cannot see the *direction* of the base, it is possible for a slow, tight "Inverted U" (Wyckoff Distribution) to pass the screener. The Kotlin backend is responsible for mathematically classifying the shape of the base and killing invalid setups.

**The Dynamic Window Requirement:**
When you upload the CSV to the backend, you must specify the **Window Length** that was used in the Chartink screener (e.g., 30, 40, or 60 days). The backend uses this number to pull the exact historical candles leading up to the validation date.

**The Polynomial Curve-Fitting Algorithm:**
To programmatically detect the shape in a robust way, the backend runs a **Quadratic Regression (Polynomial Degree 2)** across the closing prices of the window. It fits a simple curve to the data: 
$y = ax^2 + bx + c$

This mathematical formula is the holy grail for shape detection because it ignores rigid timing and smooths over daily noise. The variables **$a$ (the curvature)** and **$b$ (the slope)** tell the backend instantly what the base looks like:

**The Classification Logic:**
1. **The Inverted U / Dome (INVALID):** If **$a$ is highly negative**, the curve is an arch opening downward. This is Wyckoff Distribution.
2. **The Cup / Saucer (Valid):** If **$a$ is highly positive**, the curve is a bowl opening upward. 
3. **The Linear Shapes:** If **$a \approx 0$**, there is no curve—it is a straight line. The backend then looks at the slope ($b$):
   *   **The Flat Base (Valid):** If **$b \approx 0$**, the price is locked in horizontal equilibrium.
   *   **The Downward Drift (Valid):** If **$b < 0$**, the price is grinding lower.
   *   **The Upward Drift (Valid):** If **$b > 0$**, the price is grinding higher.

*Action:* If the backend detects a highly negative $a$ (Inverted U), it immediately tags the cluster as `DISTRIBUTION - REJECTED`.
