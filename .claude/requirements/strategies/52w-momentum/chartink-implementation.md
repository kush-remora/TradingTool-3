# 52-Week High Momentum — Chartink Implementation Guide

This document breaks down the Chartink implementation of the 52-Week High Momentum strategy (Wyckoff Phase B / Accumulation detection). 

It follows first principles: no jargon, intuitive logic, and clear observations about what each line does and why it matters.

## Market Cap Baseline Percentages

The examples in this document are targeted at **Large Cap** stocks, looking for periods of extreme compression (accumulation). 

Because different rules measure different timeframes, the standard for "tightness" changes depending on whether you are looking at a week or a single day. If you are adapting this for different segments, use these industry-standard baselines:

*   **Large Caps (Nifty 100):** 
    *   *Weekly Filter (Rule 1):* Set to `<= 5%`. (A normal week drifts 5-8%).
    *   *Daily Filter (Rule 2):* Set to `<= 3%`. (A normal day's body is 2-4%).
*   **Mid Caps (Nifty Midcap 150):** 
    *   *Weekly Filter (Rule 1):* Set to `<= 6%`.
    *   *Daily Filter (Rule 2):* Set to `<= 4%`.
*   **Small Caps (Nifty Smallcap 250):** 
    *   *Weekly Filter (Rule 1):* Set to `<= 8%`.
    *   *Daily Filter (Rule 2):* Set to `<= 5%`.
*   **Micro Caps (Nifty Microcap 250):** 
    *   *Weekly Filter (Rule 1):* Set to `<= 10%`.
    *   *Daily Filter (Rule 2):* Set to `<= 6%`.

## The Four Valid Shapes of Accumulation

When the "Composite Man" accumulates a stock, they typically do so in one of four ways:
1.  **The Flat Base (The Perfect Setup):** The stock sits in a perfectly tight, horizontal equilibrium. The price hugs the 200 DMA closely.
2.  **The Downward Drift (The Markdown/Spring):** The stock slowly grinds downward, or sharply dips below the 200 DMA (a Wyckoff Spring). They absorb supply as panicked retail traders sell.
3.  **The Upward Drift (The Slow Walk):** The stock slowly grinds upward, absorbing supply while gently walking the price higher before the explosive breakout.
4.  **The Slow Saucer / Cup (The 30-Session 'U' Shape):** The stock spends roughly 15 trading sessions slowly drifting downward, and 15 trading sessions slowly creeping back up. Chartink counts trading days, not calendar days. Since a month has about 4 trading weeks (~20 trading days), a 30-session window spans about **six trading weeks (1.5 months)**. Because the sessions are slow and quiet, it perfectly captures the Wyckoff transition from Phase A (Stopping) to Phase C (Spring) and Phase D (Markup).

**What are the INVALID shapes?**
1. **The Downward U (Rounding Top):** This is the ultimate enemy. A stock that goes up, stalls, and rounds slowly downward is in Wyckoff **Distribution** (Stan Weinstein Stage 3). The Composite Man is dumping shares, not buying them. 
2. **The Violent V-Shape:** A stock that crashes 20% in two days and rockets up 20% in two days is not being accumulated. It is just volatile chaos. Accumulation requires *time* and *boredom*. The logic in this document is designed specifically to filter out these invalid shapes.

## The Logic Breakdown

### 1. The Rolling Price Compression Check

**Chartink timeframe:** `Daily count(30, ...)` evaluates the latest **30 daily trading sessions**. Chartink counts **trading days**, not calendar days. Since a typical month has about 4 trading weeks (20 trading days), a 30-session window is basically **6 trading weeks (1.5 months)**, excluding weekends and market holidays.

**Chartink Logic:**
`Daily count( 30, 1 where  abs (  daily close -  5 days ago close ) /  5 days ago close *  100 <=  5 ) >=  18`

**What it does:**
It evaluates a 30-trading-session window (about 6 calendar weeks). For every session in that window, it checks if the price closed within 5% of where it was exactly one trading week (5 sessions) ago. It passes the stock only if this statement is true for at least 18 of those 30 sessions.

*Important Data Caveat:* While the loop evaluates 30 sessions, the *actual* historical footprint required is 35 trading sessions (about 7 calendar weeks). This is because on the oldest evaluated session, the formula must look another 5 sessions further back to find the anchor price.

**The Observation (What it reveals):**
A stock that is truly being accumulated will stop making wild swings. By comparing today's price strictly to the price 5 days ago, we measure "weekly compression." If the price barely moves week-over-week, it means the stock has flatlined and selling pressure has exhausted. 

**The Philosophy (Why it matters):**
Accumulation is silent. We aren't looking for a single quiet day; we are looking for a *habit* of quietness over time. 

Why do we require `18` days instead of a perfect `30`? Because real markets have noise, and we want to tolerate exactly **one noisy outlier per week**. 
- A 30-session window equals 6 trading weeks (5 sessions per week), or about 1.5 months (assuming 4 trading weeks per month).
- If a stock has one random noisy spike (an outlier), it breaks the math for *two* data points: the day the spike happens, and 5 days later when that spike is used as the anchor.
- 1 outlier per week x 6 weeks = 6 outliers total.
- 6 outliers x 2 broken data points = 12 failed checks.
- 30 total sessions - 12 failed checks = 18.
By setting the threshold to `18`, we are intuitively saying: *"The stock must be consistently tight, but we forgive exactly one random spike per week."*

**How to tweak this for your own study:**
- **To change the accumulation length (Number of Trading Sessions):**
  If you want to observe a longer or shorter accumulation period, you must change the lookback sessions (`30`) **AND** recalculate the passing threshold (`18`).
  *Formula for the new threshold:* 
  1. Find total trading weeks: `New Sessions / 5`
  2. Calculate allowed failures (1 mistake per week ruins 2 data points): `Total Weeks * 2`
  3. New Threshold: `New Sessions - Allowed failures`.
  *(Example for 40 sessions: 40/5 = 8 trading weeks, or about 8 calendar weeks. 8 * 2 = 16 failures. Threshold = 40 - 16 = 24. Code becomes: `Daily count( 40... ) >= 24`)*

- **To demand tighter or looser price action (Tightness %):** 
  Change `<= 3` to your desired percentage based on the Market Cap Baseline rules at the top of this document.

### 2. The Daily Footprint (Body Compression) Check

**Chartink Logic:**
`Daily count( 30, 1 where  abs (  daily close -  daily open ) /  daily open *  100 <=  3 ) >=  24`

**What it does:**
It evaluates the same 30-trading-session window (about 6 calendar weeks). For every session, it checks the difference between the open and the close price (the "body" of the candle), completely ignoring the intraday high and low. It passes if this open-close range is 3% or less on at least 24 of those 30 sessions.

**The Observation (What it reveals):**
Intraday wicks (highs and lows) are often just noise created by stop-loss hunting or temporary panic. The Open-Close is the true "daily footprint"—it represents where the market actually started and where it settled. If the open and close are consistently near each other, the daily footprint is compressed.

**The Philosophy (Why it matters):**
We are looking for calm, orderly trading. A wide open-close body means one side (buyers or sellers) is aggressively pushing the price throughout the day. A compressed body means neither side is acting with urgency. 

Why do we require `24` days here, unlike the `18` days in the previous rule? 
Because this rule only evaluates *one* day at a time. It does not anchor to a previous day. 
- In a 30-session window (6 trading weeks, about 6 calendar weeks), if we allow exactly one noisy anomaly per week, that is 6 anomalies total.
- Because an anomaly only ruins the check for the day it happens, 6 anomalies = exactly 6 failed checks.
- 30 total sessions - 6 failed checks = 24.
By setting the threshold to `24`, we are intuitively saying: *"The daily footprint must be calm, but we forgive exactly one wide, noisy day per week."*

**How to tweak this for your own study:**
- **To change the accumulation length (Number of Trading Sessions):**
  Unlike the first rule, the recalculation formula here is much simpler because 1 mistake = 1 broken session. `New Threshold = Total Sessions - Total Trading Weeks`.
  *(Example for 40 sessions: 40 sessions = 8 trading weeks, or about 8 calendar weeks. Threshold = 40 - 8 = 32. Code becomes: `Daily count( 40... ) >= 32`)*

- **To demand tighter or looser price action (Tightness %):** 
  Change `<= 1` to your desired percentage based on the Market Cap Baseline rules at the top of this document.

### 3. The Balance of Power (Up/Down Day Symmetry) Check

**Chartink Logic:**
`Abs (  Daily count( 30, 1 where  daily close >  daily open ) -  Daily count( 30, 1 where  daily close <  daily open ) ) <=  4`

**What it does:**
Over the last 30 trading sessions (about 6 calendar weeks), it counts the total number of "Up" sessions (close > open) and "Down" sessions (close < open). It then calculates the difference between those two counts. By using `Abs` (Absolute Value), it ignores whether there are more up sessions or down sessions—it only cares about the *gap* between them. It passes only if the difference is 4 or less.

**The Observation (What it reveals):**
In a trending stock, one side is clearly winning (e.g., 22 up days vs 8 down days). In an accumulating stock, the days are roughly equal (e.g., 17 up days vs 13 down days, which is a difference of exactly 4). This reveals a state of perfect equilibrium.

**The Philosophy (Why it matters):**
Accumulation is an absorption process, not a rally. When the "Composite Man" (smart money) builds a massive position, they buy quietly. When the price ticks up, they pause and let the price drift back down so they don't accidentally spark a breakout before they are ready. This creates a seesaw effect. 
By demanding the difference be `<= 4` in a 30-session window, we ensure that nobody is dominating.

**How to tweak this for your own study:**
- **To change the accumulation length (Number of Trading Sessions):**
  If you increase the sessions, you must give the gap more breathing room. A solid rule of thumb is to allow a maximum gap of ~20% of the total sessions.
  *(Example for 40 sessions: 20% of 40 is 8. Code becomes: `Abs( count... - count... ) <= 8`)*
- **Does this change by Market Cap?**
  **No.** Unlike price volatility (which swings wildly in small caps), the *balance of power* represents market equilibrium. Equilibrium looks the same whether it is a Large Cap or a Micro Cap. You do not need to adjust this threshold based on company size.

### 4. The Erratic Day (Intraday Panic) Filter

**Chartink Logic:**
`Daily count( 30, 1 where  (  daily high -  daily low ) /  daily open *  100 >=  5 ) <=  3`

**What it does:**
It looks at the same 30-trading-session window (about 6 calendar weeks). For every session, it measures the entire intraday range from the absolute lowest price to the absolute highest price (the wicks of the candle). If this range is 5% or greater, it flags the session as "erratic". It passes the stock only if there are **no more than 3** erratic sessions in the entire six-week window.

**The Observation (What it reveals):**
Rule 2 only checks the Open-Close body. A stock could open at 100, crash to 85, spike to 115, and close at 101. Rule 2 would see a tiny 1% body and think it was a perfectly calm day. Rule 4 prevents that illusion. It looks at the extreme highs and lows (wicks) and guarantees the stock didn't experience violent intraday swings.

**The Philosophy (Why it matters):**
Accumulation cannot happen in chaos. If a stock is routinely whipping up and down 5% in a single day, it means participants are panicked, stops are being hunted, and there is zero control. Smart money needs a stable floor to build a position. We allow up to `3` erratic days because occasional news shocks or market-wide drops happen, but any more than that means the stock is not in a true silent phase.

**How to tweak this for your own study:**
- **To change the accumulation length (Number of Trading Sessions):**
  If you increase the sessions (e.g., to 60 sessions, about 12 calendar weeks), you might allow up to `3` or `4` erratic sessions. The ratio is very strict: roughly 1 erratic session allowed per 15 trading sessions.
- **To adjust for Market Cap (Tightness %):** 
  Change `>= 5` based on what is considered an "erratic" move for that market cap.
  *   **Large Caps:** A 5% intraday swing is huge. Keep it at `>= 5%` or tighten to `>= 4%`.
  *   **Mid Caps:** Keep at `>= 5%`.
  *   **Small Caps:** Small caps swing 5% easily. You might loosen the definition of erratic to `>= 7%`.
  *   **Micro Caps:** Micro caps are extremely volatile. Set to `>= 8%`.

### 5. The Personality (Average Volatility) Check

**Chartink Logic:**
`Daily Ema (  (  Daily High -  Daily Low ) /  Daily Open *  100 , 30 ) <=  3.5`

**What it does:**
Instead of counting individual sessions, this calculates the **average** intraday range (High minus Low) over the entire 30-session window (about 6 calendar weeks) using an Exponential Moving Average (EMA). By using an EMA instead of a Simple Moving Average (SMA), it gives much more mathematical weight to the *most recent* sessions. It passes the stock only if its exponentially weighted average daily swing is 3.5% or less.

**The Observation (What it reveals):**
Rule 4 eliminates stocks that have occasional, isolated panic days. This rule evaluates the stock's *overall personality*. If a stock's average daily swing is massive, it means the stock is naturally loose and chaotic. But more importantly, because it uses an EMA, it looks for *progressive tightening*. A stock that was wildly volatile 25 days ago but has become dead-quiet over the last 5 days will pass an EMA check, but might fail an SMA check.

**The Philosophy (Why it matters):**
In Wyckoff theory, a stock transitions from a Markdown phase (or Phase A - Stopping the trend) into Phase B (Consolidation). Phase A is full of violent swings (Selling Climaxes, Automatic Rallies). Phase B is where supply and demand reach equilibrium and volatility dies. By using an EMA, we explicitly reward stocks that are *calming down*. A low EMA is the mathematical footprint of a stock that has successfully digested its old volatility and is now fully absorbed. 

**How to tweak this for your own study:**
- **To adjust for Market Cap (Tightness %):** 
  Because this is an *average* over 30 trading sessions, the number should be much tighter than Rule 4's outlier check. (If you use `<= 5%` for Large Caps here, you are allowing meme-stock levels of daily chaos!).
  *   **Large Caps:** Target `<= 3%` or `<= 3.5%`.
  *   **Mid Caps:** Target `<= 4%` or `<= 4.5%`.
  *   **Small Caps:** Target `<= 6.0%`.
  *   **Micro Caps:** Target `<= 7.5%`.

### 6. The Volume Dry-Up (Supply Exhaustion) Check

**Chartink Logic:**
`Daily count( 30, 1 where  daily volume <  daily sma (  daily volume , 30 ) ) >=  21`

**What it does:**
For every session in the 30-trading-session window (about 6 calendar weeks), it checks if that session's volume is *lower* than the 30-session average volume. It passes the stock only if this statement is true for at least 21 of those 30 sessions (70% of the time).

**The Observation (What it reveals):**
If a stock spends 70% of its days *below* its own long-term average volume, it means that average is being propped up by a small handful of massive volume spikes. The vast majority of the time, the stock is dead quiet.

**The Philosophy (Why it matters):**
This perfectly captures the Wyckoff Accumulation footprint. The "Composite Man" steps in with massive volume to stop a downtrend (Selling Climax) or to shake out weak hands (Spring). These few high-volume events push the 30-session SMA up. For the rest of the phase, the stock goes quiet (Supply Exhaustion) as they slowly absorb shares. By checking `volume < sma(volume, 30)`, we guarantee that the stock is structurally quiet, and that any volume spikes are rare, isolated events rather than continuous distribution.

**How to tweak this for your own study:**
- **To change the accumulation length (Number of Trading Sessions):**
  The golden ratio here is **70%**. 
  If you look back 20 sessions (about 4 calendar weeks), 70% is 14 sessions (`Daily count( 20... ) >= 14`).
  If you look back 40 sessions (about 8 calendar weeks), 70% is 28 sessions (`Daily count( 40... ) >= 28`).
- **Does this change by Market Cap?**
  **No.** Supply exhaustion is a universal concept. Volume drying up behaves the same way on a Large Cap as it does on a Micro Cap. You do not need to adjust the 70% threshold based on company size.

### 7. The Day-over-Day Volume Drop Check

**Chartink Logic:**
`Daily count( 30, 1 where  daily volume <  1 day ago volume ) >=  15`

**What it does:**
For every session in the 30-trading-session window (about 6 calendar weeks), it checks if that session's volume was strictly lower than the previous session's volume. It passes the stock only if this statement is true for at least 15 of those 30 sessions (50% of the time).

**The Observation (What it reveals):**
In a perfectly random stock, day-over-day volume will drop about 50% of the time (like a coin flip) and rise 50% of the time. By requiring the stock to hit or exceed this 50% mark, we ensure the volume is mathematically biased toward shrinking. If a stock drops day-over-day 15 to 17 times out of 30, the overall trajectory of trading activity is pointing down.

**The Philosophy (Why it matters):**
While Rule 6 measures volume against a smoothed average, this rule measures the *immediacy* of supply exhaustion. When sellers step away, they do so progressively. A stock being accumulated will frequently have days where activity just falls off a cliff compared to the day prior. By demanding that volume drops on at least half of the days, we filter out stocks that are experiencing constant daily increases in speculative excitement. 

**How to tweak this for your own study:**
- **To change the accumulation length (Number of Trading Sessions):**
  The golden ratio here is **50%**. 
  If you look back 40 sessions (about 8 calendar weeks), 50% is 20 sessions (`Daily count( 40... ) >= 20`).
- **To demand a steeper drop trajectory:**
  If you want to find stocks where volume is aggressively disappearing, increase the threshold to 60% of the sessions (e.g., `>= 18` for a 30-session window).

### 8. The Volume Contraction Streak (The "Ending Phase" Clue)

**Chartink Logic:**
`Daily countstreak( 30, 1 where  daily volume <=  1 day ago volume ) >=  3`

**What it does:**
Instead of counting total sessions across the six-week window, the `countstreak` function looks for *consecutive* sessions. It requires that somewhere in the last 30 trading sessions, there was a sequence of at least 3 sessions in a row where the volume shrank (or remained perfectly flat) every session.

**The Observation (What it reveals):**
A 3-day streak of vanishing volume means the market completely went to sleep. For three straight days, trading activity literally stepped down a staircase into silence. 

**The Philosophy (Why it matters):**
In Wyckoff theory, accumulation is a long, boring process (Phase B). But right before the stock gets ready to launch (entering Phase C or Phase D mark-up), supply dries up to an absolute trickle. The final sellers give up, creating a signature "dead pocket" of volume. If you see a 3-day streak of progressively shrinking volume inside a broader flat base, it is the ultimate clue that the accumulation phase is on the "ending side" and the stock is preparing for a move. 

**How to tweak this for your own study:**
- **To demand deeper exhaustion:** 
  You can increase this to `>= 4`. (In our real-world BHEL case study, the stock had a maximum streak of exactly 4 consecutive shrinking volume days right near the end of its accumulation phase). 
- **Does this change by Market Cap?**
  **No.** The structural footprint of final exhaustion looks exactly the same across all market caps.

### 9. The Fair Value (200 DMA) Equilibrium Check

**Chartink Logic:**
`Daily count( 30, 1 where  abs (  daily close -  daily sma (  daily close , 200 ) ) /  daily sma (  daily close , 200 ) *  100 <=  10 ) >=  15`

**What it does:**
For every session in the 30-trading-session window (about 6 calendar weeks), it checks if the closing price is within a +/- 10% band around the 200-session Simple Moving Average. It passes the stock only if the price stays inside this Fair Value band for at least 15 of 30 sessions (50% of the time).

**The Observation (What it reveals):**
The 200 DMA (a 200-trading-session moving average) is the ultimate line of "Fair Value." In a trending market, the price is far away from this line. In a consolidating market, the 200 DMA flattens out, and the price oscillates tightly around it as supply and demand reach equilibrium.

**The Philosophy (Why it matters):**
Both Wyckoff (Phase B) and Stan Weinstein (Stage 1) define accumulation as the period where the stock returns to its 200 DMA and goes sideways. However, the Composite Man will routinely push the price *below* the 200 DMA to trigger retail stop-losses (a Wyckoff Phase C "Spring"). 
Because this formula uses `abs()` (Absolute Value) and allows a `10%` variance, it brilliantly permits these intentional shakeouts. The stock can dive 9% below the 200 DMA to trap retail traders, and the scanner will still consider it valid accumulation. 

**How to tweak this for your own study:**
- **To adjust for Market Cap (Band Width %):** 
  The depth of a Phase C Shakeout depends on the size of the company.
  *   **Large Caps:** A 10% shakeout is severe. Keep the band at `<= 10%`.
  *   **Mid Caps:** They swing wider. Increase the band to `<= 15%`.
  *   **Small Caps:** Small caps frequently experience savage 20% shakeouts to clear weak hands. Increase the band to `<= 20%`.
  *   **Micro Caps:** Micro caps can experience even deeper shakeouts. Increase the band to `<= 25%`.
- **To change the accumulation length (Number of Trading Sessions):**
  The threshold here is roughly 50%. So for a 40-session window (about 8 calendar weeks), set the count to `20`.

### 10. The Distance from Breakout (52-Week High Proximity)

**Chartink Logic:**
`( 1 day ago Max ( 250 ,  Daily High ) -  Daily Close ) /  1 day ago Max ( 250 ,  Daily High ) *  100 >=  20`

**What it does:**
It ensures the accumulation base is forming at least 20% *below* the 52-week high (far away from it).

**The Philosophy (Why it matters):**
This system is designed as a **backward-sweep** from a 52-week high trigger. If a stock hits a 52-week high today, we want to look backward in time to see if the Composite Man built a massive cause *before* the run. 
If the accumulation base is sitting at 0% or 5% away from the 52-week high, it means there was no markup (Phase D)—the stock is just sitting at the top. True accumulation must happen deep below the high (e.g., 20% to 50% below). The base is the *Cause*, and the 20%+ run up to the 52-week high is the *Effect*. This rule mathematically forces the scanner to find the true, deep accumulation floors that preceded the massive runs.
