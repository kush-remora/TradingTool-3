# Statistical Mean Reversion Waterfall V1

**Created:** May 7, 2026  
**Source discussion:** Codex thread on Bollinger Bands, RSI, Z-score / DMA, and VWAP for dip-buying in strong stocks

## Purpose

Turn the current discussion into a clean product and strategy note for a daily mean-reversion scanner that can later be implemented, backtested, and refined.

This document describes a hypothesis and operating model. It does not claim edge or production readiness.

## Core Idea

Strong stocks do not move in a straight line. Even fundamentally solid names in broad Indian benchmarks often pull back, become temporarily stretched below their recent mean, and then revert toward a more normal price zone.

The goal is to:

- detect statistically stretched dips,
- avoid buying every weak stock blindly,
- wait for simple evidence that the dip is stabilizing,
- and sell when price reverts toward fair value or becomes extended on the upside.

This is best described as:

`daily mean reversion with optional confirmation`

## Strategy Goal

From a watchlist of roughly 100 stocks, reduce the list step by step until only a small number of high-quality dip opportunities remain.

Desired trade style:

- timeframe: daily swing
- holding period: roughly 1 week to 3 months
- universe: strong, liquid Indian equities
- decision style: rule-based, explainable, and backtestable

## Indicator Roles

These indicators do different jobs. They should be treated as layers, not substitutes.

### Bollinger Bands

Use Bollinger Bands as the primary visual and statistical stretch detector.

What it answers:

- has price moved unusually far below its recent mean?
- is the stock trading near or below a lower statistical envelope?

### Z-Score or DMA

Use one numeric distance measure to quantify how far price is from the mean.

What it answers:

- exactly how stretched is price versus its recent average?

Working choices:

- `Z-score`: `(price - moving average) / standard deviation`
- `DMA %`: `((price - moving average) / moving average) * 100`

Use one as the primary ranking metric. Keep the other optional for comparison.

### RSI

Use RSI as a momentum exhaustion filter, not as the main mean anchor.

What it answers:

- is downside momentum overextended?
- is the stock weak enough to deserve attention?

### VWAP

Use VWAP as a fair-value anchor only when it adds value to the holding horizon.

What it answers:

- is price also stretched versus the volume-weighted traded zone?

For this strategy:

- daily swing version: optional or secondary
- intraday confirmation version: much more useful

VWAP should not be the first gate in v1.

## High-Level Waterfall

Given 100 watchlist stocks, the system should reduce them through a simple waterfall.

### Step 1: Universe Filter

Start with all 100 stocks.

Keep only stocks that satisfy basic tradability and quality rules:

- adequate liquidity
- no obvious junk names
- preferably benchmark or curated fundamentally strong names

Output:

- reduced candidate pool

### Step 2: Define the Mean

For each remaining stock, compute the baseline mean.

Default v1 choice:

- `20-day SMA`

Supporting calculations:

- Bollinger Bands based on the same 20-day lookback
- standard deviation over the same 20-day window
- optional anchored or session VWAP when intraday data is available

Output:

- each stock now has a current fair-value reference

### Step 3: Detect Statistical Stretch

Find stocks that are materially below the mean.

Primary triggers may include:

- close at or below lower Bollinger Band
- Z-score below a negative threshold
- DMA % below a negative threshold

Example logic:

- `close <= lower_band`
- `z_score <= -2.0`
- `dma_pct <= -5%`

The exact threshold is not final and must be backtested.

Output:

- only statistically stretched stocks continue

### Step 4: Confirm Oversold Momentum

Use RSI to confirm that weakness is meaningful.

Possible rules:

- `RSI(14) <= 30`
- or `RSI(14)` near its recent low range

This step should answer:

- is the stock actually washed out, or merely drifting lower?

Output:

- oversold subset of statistically stretched stocks

### Step 5: Check Footprint / Participation

Look for signs that the move matters.

Useful checks:

- volume above recent average
- delivery or participation spike if available
- large reaction near the lower band or low zone

Interpretation:

- this is a supporting quality filter, not the main trigger

Output:

- oversold names with meaningful market participation

### Step 6: Optional Fair-Value Cross-Check

If VWAP is available and the use case supports it, compare current price with VWAP.

Possible interpretation:

- price materially below VWAP strengthens the fair-value dislocation case
- price reclaiming VWAP can later act as a confirmation event

For v1 daily implementation, this step can stay optional.

Output:

- additional context, not mandatory gating

### Step 7: Confirmation Layer

Decide whether the strategy is pure mean reversion or mean reversion with confirmation.

#### Option A: Pure Mean Reversion

Enter when statistical stretch and oversold conditions are met.

Pros:

- earlier entry
- better average price

Cons:

- more false catches
- more falling-knife risk

#### Option B: Mean Reversion With Confirmation

Enter only after stretch is detected and rebound evidence appears.

Possible confirmation signals:

- RSI turns up
- close moves back above lower Bollinger Band
- close breaks above recent pivot high
- price reclaims short moving average
- price reclaims VWAP in intraday version

Pros:

- safer entries
- lower false-positive rate

Cons:

- later entry
- reduced reward if bounce is sharp

Output:

- trade-ready names

### Step 8: Entry State Assignment

Each stock should land in one clear state:

- `NORMAL`
- `STRETCHED`
- `OVERSOLD`
- `FOOTPRINT_DETECTED`
- `CONFIRMATION_PENDING`
- `TRIGGERED`
- `EXITED`
- `INVALIDATED`

This is better than showing only raw indicator values in the UI.

### Step 9: Exit Rules

The first version should use simple reversion exits.

Working choices:

- exit at 20-day SMA
- exit at Bollinger middle band
- exit when Z-score normalizes back toward 0
- exit when price reaches upper Bollinger region
- stop-loss if price continues breaking down

The strategy should prefer simple and explainable exits over dynamic complexity.

## Suggested V1 Rules

If we want the simplest usable daily scanner, use:

- universe: curated strong-stock watchlist
- mean: `20-day SMA`
- stretch: `close <= lower Bollinger Band`
- distance rank: `Z-score` as the main numeric sort key
- oversold filter: `RSI(14) <= 30`
- participation filter: volume above 20-day average
- trigger: either immediate scan hit or one confirmation rule
- exit: middle band / 20-day SMA

## Difference From the RSI-Only Confirmation Setup

This document is broader than the existing RSI-floor setup.

The earlier setup starts from:

- rare RSI weakness,
- then watches for stabilization and breakout confirmation.

This setup starts from:

- price deviation from a statistical mean,
- then uses RSI as a supporting oversold filter.

In short:

- RSI-floor setup: momentum exhaustion first
- Bollinger / Z-score setup: price deviation first

They are related, but not identical.

## Acceptance Criteria For Future Implementation

A future implementation should:

- compute Bollinger Bands, Z-score or DMA, and RSI for each stock in the watchlist
- reduce the watchlist through the waterfall in a deterministic order
- show each stock's current state in the waterfall
- explain why the stock passed or failed each step
- support both pure mean-reversion mode and confirmation mode
- expose simple configurable thresholds without excessive tuning complexity

## Out of Scope

This document does not define:

- broker automation
- portfolio sizing logic
- advanced optimization
- ML prediction
- multi-timeframe strategy stacking
- production-grade intraday VWAP execution logic

## Complexity Estimate

For a first implementation:

- backend scanner logic: 1 to 2 days
- state model and API response: 0.5 to 1 day
- simple UI waterfall/table: 1 day
- first backtest / threshold sweep: 1 to 2 days

## Open Questions

These should be decided by backtesting, not opinion:

- should Z-score or DMA be the primary ranking metric?
- should confirmation be mandatory or optional?
- which exit works best: SMA, middle band, or partial scaling?
- does VWAP add daily-swing value, or only intraday value?
- what threshold best separates normal pullbacks from true breakdowns?
