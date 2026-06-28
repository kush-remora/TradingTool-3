# 52-Week High Momentum — Chartink Implementation Guide

This document breaks down the Chartink implementation of the 52-Week High Momentum strategy (Wyckoff Phase B / Accumulation detection). 

It follows first principles: no jargon, intuitive logic, and clear observations about what each line does and why it matters.

## Market Cap Baseline Percentages

The examples in this document are targeted at **Large Cap** stocks, looking for periods of extreme compression (accumulation). 

Because different rules measure different timeframes, the standard for "tightness" changes depending on whether you are looking at a week or a single day. If you are adapting this for different segments, use these industry-standard baselines:

*   **Large Caps (e.g., Nifty 50):** 
    *   *Weekly Filter (Rule 1):* Set to `<= 2%` or `<= 3%`. (A normal week drifts 3-5%).
    *   *Daily Filter (Rule 2):* Set to `<= 1%` or `<= 1.5%`. (A normal day's body is 1.5-2.5%).
*   **Mid Caps:** 
    *   *Weekly Filter (Rule 1):* Set to `<= 4%`.
    *   *Daily Filter (Rule 2):* Set to `<= 2%` or `<= 2.5%`.
*   **Small/Micro Caps:** 
    *   *Weekly Filter (Rule 1):* Set to `<= 5%` or `<= 6%`.
    *   *Daily Filter (Rule 2):* Set to `<= 3%` or `<= 4%`.

## The Logic Breakdown

### 1. The Rolling Price Compression Check

**Chartink Logic:**
`Daily count( 30, 1 where  abs (  daily close -  5 days ago close ) /  5 days ago close *  100 <=  3 ) >=  18`

**What it does:**
It evaluates a 30-day window (6 weeks of checks). For every single day in that window, it checks if the price closed within 3% of where it was exactly one week (5 days) ago. It passes the stock only if this statement is true for at least 18 out of those 30 days.

*Important Data Caveat:* While the loop runs 30 times, the *actual* historical footprint required is 35 days. This is because on the 30th day back (the oldest day being evaluated), the formula must look another 5 days further back to find the anchor price.

**The Observation (What it reveals):**
A stock that is truly being accumulated will stop making wild swings. By comparing today's price strictly to the price 5 days ago, we measure "weekly compression." If the price barely moves week-over-week, it means the stock has flatlined and selling pressure has exhausted. 

**The Philosophy (Why it matters):**
Accumulation is silent. We aren't looking for a single quiet day; we are looking for a *habit* of quietness over time. 

Why do we require `18` days instead of a perfect `30`? Because real markets have noise, and we want to tolerate exactly **one noisy outlier per week**. 
- A 30-day window equals 6 trading weeks (5 days per week).
- If a stock has one random noisy spike (an outlier), it breaks the math for *two* data points: the day the spike happens, and 5 days later when that spike is used as the anchor.
- 1 outlier per week x 6 weeks = 6 outliers total.
- 6 outliers x 2 broken data points = 12 failed checks.
- 30 total days - 12 failed checks = 18.
By setting the threshold to `18`, we are intuitively saying: *"The stock must be consistently tight, but we forgive exactly one random spike per week."*

**How to tweak this for your own study:**
- **To change the accumulation length (Number of Days):** 
  If you want to observe a longer or shorter accumulation period, you must change the lookback days (`30`) **AND** recalculate the passing threshold (`18`). 
  *Formula for the new threshold:* 
  1. Find total weeks: `New Days / 5`
  2. Calculate allowed failures (1 mistake per week ruins 2 data points): `Total Weeks * 2`
  3. New Threshold: `New Days - Allowed failures`. 
  *(Example for 40 days: 40/5 = 8 weeks. 8 * 2 = 16 failures. Threshold = 40 - 16 = 24. Code becomes: `Daily count( 40... ) >= 24`)*

- **To demand tighter or looser price action (Tightness %):** 
  Change `<= 3` to your desired percentage based on the Market Cap Baseline rules at the top of this document.

### 2. The Daily Footprint (Body Compression) Check

**Chartink Logic:**
`Daily count( 30, 1 where  abs (  daily close -  daily open ) /  daily open *  100 <=  1 ) >=  24`

**What it does:**
It evaluates the same 30-day window. For every single day, it checks the difference between the open and the close price (the "body" of the candle), completely ignoring the intraday high and low. It passes if this open-close range is 1% or less on at least 24 of those 30 days.

**The Observation (What it reveals):**
Intraday wicks (highs and lows) are often just noise created by stop-loss hunting or temporary panic. The Open-Close is the true "daily footprint"—it represents where the market actually started and where it settled. If the open and close are consistently near each other, the daily footprint is compressed.

**The Philosophy (Why it matters):**
We are looking for calm, orderly trading. A wide open-close body means one side (buyers or sellers) is aggressively pushing the price throughout the day. A compressed body means neither side is acting with urgency. 

Why do we require `24` days here, unlike the `18` days in the previous rule? 
Because this rule only evaluates *one* day at a time. It does not anchor to a previous day. 
- In a 30-day window (6 weeks), if we allow exactly one noisy anomaly per week, that is 6 anomalies total.
- Because an anomaly only ruins the check for the day it happens, 6 anomalies = exactly 6 failed checks.
- 30 total days - 6 failed checks = 24.
By setting the threshold to `24`, we are intuitively saying: *"The daily footprint must be calm, but we forgive exactly one wide, noisy day per week."*

**How to tweak this for your own study:**
- **To change the accumulation length (Number of Days):** 
  Unlike the first rule, the recalculation formula here is much simpler because 1 mistake = 1 broken day. `New Threshold = Total Days - Total Weeks`. 
  *(Example for 40 days: 40 days = 8 weeks. Threshold = 40 - 8 = 32. Code becomes: `Daily count( 40... ) >= 32`)*

- **To demand tighter or looser price action (Tightness %):** 
  Change `<= 1` to your desired percentage based on the Market Cap Baseline rules at the top of this document.

### 3. The Balance of Power (Up/Down Day Symmetry) Check

**Chartink Logic:**
`Abs (  Daily count( 30, 1 where  daily close >  daily open ) -  Daily count( 30, 1 where  daily close <  daily open ) ) <=  4`

**What it does:**
Over the last 30 days, it counts the total number of "Up" days (close > open) and "Down" days (close < open). It then calculates the difference between those two counts. By using `Abs` (Absolute Value), it ignores whether there are more up days or down days—it only cares about the *gap* between them. It passes only if the difference is 4 or less.

**The Observation (What it reveals):**
In a trending stock, one side is clearly winning (e.g., 22 up days vs 8 down days). In an accumulating stock, the days are roughly equal (e.g., 17 up days vs 13 down days, which is a difference of exactly 4). This reveals a state of perfect equilibrium.

**The Philosophy (Why it matters):**
Accumulation is an absorption process, not a rally. When the "Composite Man" (smart money) builds a massive position, they buy quietly. When the price ticks up, they pause and let the price drift back down so they don't accidentally spark a breakout before they are ready. This creates a seesaw effect. 
By demanding the difference be `<= 4` in a 30-day window, we ensure that nobody is dominating. 

**How to tweak this for your own study:**
- **To change the accumulation length (Number of Days):** 
  If you increase the days, you must give the gap more breathing room. A solid rule of thumb is to allow a maximum gap of ~20% of the total days. 
  *(Example for 40 days: 20% of 40 is 8. Code becomes: `Abs( count... - count... ) <= 8`)*
- **Does this change by Market Cap?**
  **No.** Unlike price volatility (which swings wildly in small caps), the *balance of power* represents market equilibrium. Equilibrium looks the same whether it is a Large Cap or a Micro Cap. You do not need to adjust this threshold based on company size.

### 4. The Erratic Day (Intraday Panic) Filter

**Chartink Logic:**
`Daily count( 30, 1 where  (  daily high -  daily low ) /  daily open *  100 >=  5 ) <=  2`

**What it does:**
It looks at the same 30-day window. For every single day, it measures the entire intraday range from the absolute lowest price to the absolute highest price (the wicks of the candle). If this range is 5% or greater, it flags the day as "erratic". It passes the stock only if there are **no more than 2** erratic days in the entire month and a half.

**The Observation (What it reveals):**
Rule 2 only checks the Open-Close body. A stock could open at 100, crash to 85, spike to 115, and close at 101. Rule 2 would see a tiny 1% body and think it was a perfectly calm day. Rule 4 prevents that illusion. It looks at the extreme highs and lows (wicks) and guarantees the stock didn't experience violent intraday swings.

**The Philosophy (Why it matters):**
Accumulation cannot happen in chaos. If a stock is routinely whipping up and down 5% in a single day, it means participants are panicked, stops are being hunted, and there is zero control. Smart money needs a stable floor to build a position. We allow up to `2` erratic days because occasional news shocks or market-wide drops happen, but any more than that means the stock is not in a true silent phase.

**How to tweak this for your own study:**
- **To change the accumulation length (Number of Days):** 
  If you increase the days (e.g., to 60 days), you might allow up to `3` or `4` erratic days. The ratio is very strict: roughly 1 erratic day allowed per 15 trading days.
- **To adjust for Market Cap (Tightness %):** 
  Change `>= 5` based on what is considered an "erratic" move for that market cap.
  *   **Large Caps:** A 5% intraday swing is huge. Keep it at `>= 5%` or tighten to `>= 4%`.
  *   **Mid Caps:** Keep at `>= 5%`.
  *   **Small/Micro Caps:** Small caps swing 5% easily. You might loosen the definition of erratic to `>= 7%` or `>= 8%`.
