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

