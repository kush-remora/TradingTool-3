# Phase D (Markup) Momentum — Chartink Implementation Guide

This document breaks down the Chartink implementation for detecting Wyckoff Phase D (Markup/Ignition) following a period of accumulation. 

It follows first principles: no jargon, intuitive logic, and clear observations about what each line does and why it matters.

## The Logic Breakdown

### 1. The Exhaustion Cluster (The "Dead Pocket") Check

**Chartink Logic:**
`Daily count( 10, 1 where  daily volume <  (  1 day ago sma (  daily volume , 50 ) *  0.50 ) ) >  5` 
*(Note: `> 5` is mathematically the same as `>= 6`. See Market Cap rules below for the multiplier)*

**What it does:**
It looks at the last 10 trading days (two calendar weeks). It checks if *at least 6 of those 10 days* experienced a massive volume drop (falling below 50% of the 50-day average). Importantly, it anchors the 50-day average to *yesterday* (`1 day ago`) so today's volume doesn't accidentally skew the baseline.

**The Observation (What it reveals):**
Rather than looking for a single quiet day (which could just be a fluke or a pre-holiday session), this looks for a prolonged "cluster" of exhaustion. It proves that for the majority of the last two weeks, trading activity completely stalled out. 

**The Philosophy (Why it matters):**
In Wyckoff methodology, right at the end of Phase C (just before the Phase D markup begins), the stock forms a true "dead pocket." The sellers are gone, but the buyers haven't stepped back in to spark momentum yet. By demanding that at least 6 out of the last 10 days are practically dead, we mathematically guarantee we have found a highly stable floor right before the ignition. 

**Market Cap Baseline Adjustments:**
Because liquidity behavior changes drastically based on company size, the multiplier (the `0.50` in the formula) must be adjusted depending on which segment you are scanning:
*   **Large Caps (Nifty 100):** Use `* 0.50`. Large caps are heavily traded by ETFs and never completely stop. A 50% drop is a severe and valid exhaustion signal.
*   **Mid Caps:** Use `* 0.40`. 
*   **Small/Micro Caps:** Use `* 0.25`. Micro caps can experience total liquidity vacuums. You must demand that volume practically vanishes to avoid false signals in noisy small caps.

**How to tweak this for your own study:**
*   **The 5-Day vs 10-Day Window:** 
    *   If you want to catch a very rapid, short-term exhaustion (a tight 1-week pause), change the logic to a 5-day window: `count( 5 ... ) > 2` (at least 3 out of 5 days dead). 
    *   If you want a more robust, guaranteed dead pocket (a 2-week pause), keep it at the 10-day window: `count( 10 ... ) > 5` (at least 6 out of 10 days dead).

### 2. The Two-Day Volume Surge (Ignition)

**Chartink Logic:**
`Daily countstreak( 2, 0 where  daily volume >  ( 1 day ago sma ( daily volume , 20 ) * 1.5 ) ) >=  2`

**What it does:**
It looks precisely at the last 2 days (Yesterday and Today) and demands that *both* days experienced a massive volume surge (e.g., at least 50% above the recent 20-day average).

**The Observation (What it reveals):**
A single day of high volume could be random retail hype or short-covering. Two consecutive days of explosive volume proves that aggressive, sustained institutional buying has officially begun *right now*. 

**The Philosophy (Why it matters):**
In Wyckoff methodology, Phase D markup must begin with a true "Sign of Strength" (SOS) backed by expanding volume. By demanding consecutive high-volume days, we confirm that the "dead pocket" is over and the Composite Man is actively marking up the price.

**Is the 20-day SMA universal?**
**Yes.** While we used a 50-day average for the exhaustion check, a 20-day average (one trading month) is the perfect baseline for an ignition trigger. You want to measure the new volume surge against the *most recent* quiet period of the base. If you used a 50-day average here, the baseline might be artificially inflated by old volume from Phase A/B, making it too hard to trigger the 1.5x multiplier.

**Market Cap Baseline Adjustments (The Multiplier):**
The `1.5` multiplier (a 50% surge) is **not universal**. It must scale drastically based on the liquidity of the segment:
*   **Large Caps (Nifty 100):** Use `* 1.5`. A 50% surge over two consecutive days on a heavily traded giant (like Reliance or HDFC) requires massive institutional capital. It is a powerful signal.
*   **Mid Caps:** Use `* 2.0`. Mid caps have more fluid liquidity, so demand a true 200% volume shock to confirm the breakout.
*   **Small/Micro Caps:** Use `* 3.0` or `* 4.0`. Small caps can randomly double in volume on retail news or telegram tips. To prove an undeniable institutional footprint in a small cap, you need to demand a massive 300% to 400% volume explosion.

### 3. The Price Action Confirmation (The Green Streak & Upward Progress)

**Chartink Logic:**
`Daily countstreak( 2, 0 where ( Daily Close > Daily Open and Daily Close > 1 day ago Close ) ) >=  2`

**What it does:**
It looks at the last 2 days (Yesterday and Today) and demands two things:
1. Both days closed higher than they opened (green candles).
2. Both days closed strictly higher than the previous day's close.

**The Observation (What it reveals):**
It confirms that the stock is consistently grinding upward. By checking `Close > 1 day ago Close`, it prevents the "Gap-Down Trap" (where a stock crashes at the open but closes slightly green, registering a false positive). It proves buyers are making real, physical upward progress on the chart.

**The Philosophy (Why it matters):**
In Wyckoff methodology, high volume is meaningless on its own. If you have a massive volume surge but the candle is red (`Close < Open`), that is a **Sign of Weakness (SOW)** or a Selling Climax—institutions are dumping shares. By forcing green, climbing candles concurrently with the 2-day volume surge, we mathematically guarantee that the volume explosion is pure **Demand**.

**Does this change by Market Cap?**
**No.** Price action is universal. A green, climbing candle means buyers won the day across all caps.

### 4. The Strong Close (No Rejection)

**Chartink Logic:**
`Daily countstreak( 2, 0 where  daily close >=  (  daily high -  (  daily high -  daily low ) *  0.25 ) ) >=  2` *(See Market Cap rules below for the multiplier)*

**What it does:**
It looks at the last 2 days and demands that the closing price was strictly within the **top 25%** of the entire daily range (High minus Low). 

**The Observation (What it reveals):**
It mathematically filters out "Shooting Stars" and candles with long upper wicks. It proves that there was no end-of-day selling pressure pushing the price down from its highs.

**The Philosophy (Why it matters):**
In Wyckoff, a true markup is orchestrated by institutional money. Retail traders often take intraday profits, creating upper wicks. Institutions, however, absorb that supply and pin the price near the absolute high to trap short sellers overnight. Closing near the high of the range is the ultimate footprint of institutional control holding steady through the closing bell.

**Market Cap Baseline Adjustments:**
While the *philosophy* of a strong close is universal, the strictness of the multiplier (the `0.25`) must be adjusted to forgive the natural volatility of smaller companies:
*   **Large Caps (Nifty 100):** Use `* 0.25` (Top 25%). Large caps are highly liquid and orderly. Institutions can easily pin the close near the absolute high. You can even tighten this to `* 0.20` for extreme strictness.
*   **Mid Caps:** Use `* 0.25`. This remains the sweet spot.
*   **Small/Micro Caps:** Use `* 0.30` or `* 0.35` (Top 30-35%). Small caps suffer from wide bid/ask spreads and erratic retail profit-booking near 3:15 PM. If you demand a strict 25%, you will frequently filter out perfectly valid Markups just because of natural market noise. Loosening the requirement forgives this volatility while still successfully rejecting true shooting stars.

### 5. The Macro Trend Guardrails (Stage 2 Confirmation)

**Chartink Logic:**
`Daily Sma ( Daily Close , 20 ) > Daily Sma ( Daily Close , 50 )`
`Daily Close > Daily Sma ( Daily Close , 100 )`

**What it does:**
It ensures the short-term momentum (20 DMA) has crossed above the medium-term average (50 DMA), and the price is safely trading above the long-term macro trend (100 DMA).

**The Architectural Insight (Why we deleted the 20-day high breakout):**
You correctly identified that Chartink cannot find the true resistance of the accumulation base. Because we do not know if the base is 30, 60, or 90 days long, a 20-day high check is useless and might filter out valid stocks. **Therefore, we have completely removed the breakout check from Chartink.** We use Chartink *only* to find the Volume Ignition, and leave 100% of the resistance/breakout math to the Kotlin backend.

**The Philosophy (Why the SMAs are different and necessary):**
These Moving Average rules have nothing to do with measuring the accumulation base or its resistance. They are purely **Macro Safety Guardrails**. 
We know that we *never* want to buy a stock that is crashing in a macro downtrend (Stan Weinstein Stage 4). If a stock experiences a massive volume surge but is trading below its 100 DMA, that is likely a "Dead Cat Bounce" or short-covering rally, not a true Wyckoff Phase D markup. 
By forcing the 20 DMA > 50 DMA, and Price > 100 DMA, we mathematically guarantee that the stock's macro gravity is curling upward (Stage 2). 

**Does this change by Market Cap?**
**No.** Macro trend physics (like avoiding stocks trading below their 100 DMA) apply to all stocks equally.

