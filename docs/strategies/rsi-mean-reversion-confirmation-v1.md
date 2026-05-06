# RSI Mean Reversion With Confirmation V1

**Created:** May 6, 2026  
**Source discussion:** `/Users/kushbhardwaj/Documents/Gemini chat/mean reversion RSI based discussion.pdf`

## Purpose

Turn the recent strategy discussion into a clean requirements note that we can later implement, review, or reject without re-reading the full PDF transcript.

This document is not backtest proof. It is a strategy hypothesis and product requirements note.

## Core Idea

We are trying to build a daily swing-trading setup that:

- starts from an extreme RSI weakness condition,
- avoids buying just because RSI is low,
- distinguishes between "still falling" and "likely stabilizing,"
- waits for real confirmation from both price and RSI,
- and targets a rebound move instead of predicting the exact bottom.

This is best described as:

`mean reversion with confirmation`

It is **not**:

- pure bottom-fishing,
- pure momentum breakout,
- or a full fundamental ranking system.

## Strategy Goal

Find stocks that were heavily sold, show evidence that the selling pressure is weakening, and then trigger only when the rebound becomes visible in both price and RSI.

Desired trade style:

- timeframe: daily swing
- holding period: a few days to a few weeks
- effort level: low screen time
- decision style: rule-based, reviewable, and backtestable

## Non-Goals

This document does not define:

- intraday execution logic,
- broker automation,
- final position sizing rules,
- production dashboard layout,
- or proven parameter values.

It also does not assume we should immediately trade this live.

## High-Level Flow

The working sequence is:

1. Find extreme RSI weakness.
2. Check whether RSI is improving or still deteriorating.
3. Compare RSI behavior with price behavior.
4. Move the stock into a watch state if weakness is slowing.
5. Wait for price and RSI to strengthen together.
6. Trigger only after a real breakout / pivot confirmation.
7. Manage exit with fixed rules and review the result afterward.

## Working Definitions

### 1. RSI Floor

The first filter is an extreme RSI event.

Initial working requirement:

- current RSI(14), or the latest recent RSI reading, touches the lowest RSI seen in the last 1 year

Equivalent practical phrasing:

- RSI is at or very near its 1-year low

This is the "attention" event. It does **not** mean buy.

### 2. RSI Improvement

After the floor event, the next question is whether momentum is still getting worse or starting to improve.

We want to separate two cases:

- `bad case`: price is going down and RSI is also going down
- `better case`: price is still weak or making a lower low, but RSI is flattening or improving

This is the core "falling knife vs. exhaustion" distinction.

### 3. Price and RSI Alignment

The next part of the setup is the relationship between price and RSI:

- `bearish continuation`: price down, RSI down
- `possible exhaustion`: price down, RSI flat or up
- `recovery confirmation`: price up, RSI up

The strategy should only move toward entry when the setup progresses from the first state toward the third state.

## Stage-by-Stage Requirements

## Stage 1: Extreme Weakness Filter

### Objective

Reduce the universe to names that are truly stretched on momentum, not just mildly weak.

### Initial Rules

- Use daily candles.
- Compute RSI(14).
- Compare the latest RSI to the trailing 1-year RSI range.
- Mark a stock as a candidate when RSI touches, matches, or is within a small tolerance of the lowest RSI in the last 1 year.

### Notes

- We may later decide whether the condition should be:
  - exact 1-year low,
  - within X percent of the 1-year RSI low,
  - or below an absolute floor like 20 or 25.
- For now, the strategy intent is "rare RSI weakness," not generic oversold.

## Stage 2: Weakness Classification

### Objective

Avoid buying stocks where the weakness is still accelerating.

### Required Comparison

For each Stage 1 candidate, classify recent behavior into one of these buckets:

- `CONTINUING_WEAKNESS`
  - price down
  - RSI down
- `EARLY_STABILIZATION`
  - price down or flat
  - RSI flat or rising
- `RECOVERY_STARTING`
  - price up
  - RSI up

### Product Meaning

- `CONTINUING_WEAKNESS` should not be trade-ready.
- `EARLY_STABILIZATION` is a watchlist state.
- `RECOVERY_STARTING` is eligible to move into breakout confirmation checks.

### Why This Matters

This is the main protection against naive mean reversion. Low RSI alone is not a signal. The slope and relationship matter.

## Stage 3: Divergence / Exhaustion Interpretation

### Objective

Identify whether price weakness is losing force.

### Desired Pattern

The strongest pre-confirmation pattern is:

- price makes a lower low or remains weak,
- but RSI no longer makes a comparable lower low,
- or RSI begins rising while price remains soft

This is a practical divergence-style interpretation, even if we do not initially label it with strict textbook divergence rules.

### Requirement

The system should preserve enough history in the candidate row to show:

- prior RSI low
- current RSI
- prior price low
- current price
- direction of recent RSI slope
- direction of recent price slope

This is important for later review and backtesting explainability.

## Stage 4: Setup Quality Filters

### Objective

Improve candidate quality before we wait for a breakout.

### Current Working Filters

- volume footprint should be notable around or after the weakness event
- unusual volume is a positive sign that something changed
- liquidity must be sufficient to avoid unusable setups

### Interpretation

Volume is a supporting filter, not the primary trigger.

We are not trying to buy because volume is high. We are trying to say:

- "this weak stock is not random; market participation increased here"

### Open Choice

The exact volume rule is not final. Examples from the discussion included:

- 3x to 5x average volume
- delivery-style accumulation checks

These should stay hypotheses until backtested.

## Stage 5: Breakout / Pivot Confirmation

### Objective

Only enter when the recovery becomes visible in both price and RSI.

### Required Shape

The final trigger should happen only when:

- price starts moving up
- RSI starts moving up
- and price clears a practical pivot / breakout threshold

### Working Trigger Options

At least one confirmation rule should be chosen for implementation and backtesting:

- close above recent 3-day high
- close above recent pivot high
- rebound above recent low by an ATR-based threshold
- RSI crosses a recovery threshold while price also confirms

### Current Direction

Based on the discussion, the preferred interpretation is:

- do not trigger on a tiny rebound
- do not trigger on price-only bounce
- require both price and RSI to improve together

In plain language:

- "buy when the bounce becomes real, not when the fall merely pauses"

## Stage 6: Entry State

### Objective

Translate the setup into a clean system state instead of an ambiguous dashboard label.

### Suggested State Machine

- `SCAN`
  - full universe
- `RSI_FLOOR_HIT`
  - 1-year RSI floor condition met
- `WATCH_STABILIZATION`
  - RSI improving but price not confirmed yet
- `PENDING_BREAKOUT`
  - setup quality is acceptable; waiting for price + RSI confirmation
- `READY`
  - trigger conditions met
- `ACTIVE_TRADE`
  - position entered
- `EXITED_WIN`
  - target or strong exit reached
- `EXITED_LOSS`
  - stop or invalidation hit
- `INVALIDATED`
  - RSI floor setup failed before trigger

This state model should guide both the backend model and the eventual dashboard.

## Stage 7: Exit and Risk Rules

### Objective

Keep the system simple enough to test and review.

### Initial Working Assumption

The conversation often referenced:

- a fixed upside target in the 5% to 7% range
- a fixed or structure-based stop loss

### Requirement

The first backtestable version should support at least one simple exit model:

- target percent
- stop percent
- optional time stop

Examples:

- take profit at +5% to +7%
- stop loss at recent low or fixed percent below entry

These numbers are not final. They must remain configurable.

## Strategy Logic in Plain English

The intended story is:

1. A stock becomes extremely weak on RSI.
2. We do not buy immediately.
3. We ask whether the weakness is still getting worse or beginning to slow.
4. If RSI improves while price is still weak, we treat that as early evidence, not confirmation.
5. We wait until both price and RSI begin rising together.
6. Only after that do we accept a breakout or pivot as a valid entry trigger.

This is the simplest durable summary of the discussion so far.

## Data Requirements

Minimum data needed:

- daily OHLCV candles
- RSI(14)
- trailing 1-year RSI history
- recent price highs/lows
- recent RSI highs/lows

Useful but optional later:

- delivery percentage
- market cap
- traded value / liquidity filters
- sector-relative strength

## Dashboard Implications

If we build a dashboard for this strategy later, it should reflect the lifecycle instead of mixing unrelated pages.

Recommended dashboard buckets:

- `RSI Floor Hits`
- `Stabilizing`
- `Pending Breakout`
- `Ready`
- `Active Trades`
- `Reviewed / Closed`

This is better than building separate half-complete dashboards for scanner, backtest, and live ideas.

## Backtesting Requirements

Before calling this a live strategy, backtesting must answer:

- How often does a 1-year RSI floor actually lead to a tradable rebound?
- Is RSI improvement before price confirmation materially useful?
- Which breakout rule works best after stabilization?
- What holding period captures the move best?
- How many signals are junk without liquidity and volume filters?
- Does the strategy work better in large caps, mid caps, or small caps?

## Risks and Failure Modes

Main risks:

- buying a falling knife
- overfitting thresholds like RSI level, volume multiple, or stop size
- mixing too many filters into one unreadable system
- turning a research idea into a bloated dashboard before proving it

## Explicit Scope for V1

The first clean implementation should focus only on:

- daily timeframe
- RSI 1-year floor detection
- recent RSI direction vs recent price direction
- a simple breakout confirmation rule
- a simple exit model
- candidate state visibility

Do not mix V1 with:

- Piotroski scoring
- deep fundamentals
- complex sector models
- intraday execution logic
- too many alternate triggers

## Open Questions

These still need research or product decisions:

- What exact tolerance defines "touching" the 1-year RSI low?
- Should price weakness classification use 3 days, 5 days, or 10 days?
- What is the cleanest breakout rule for confirmation?
- Should volume be mandatory or only a ranking bonus?
- Should the first live universe be all NSE, a liquid subset, or a watchlist universe?
- Should exits be fixed percent, ATR-based, or structure-based?

## Working One-Line Summary

This strategy looks for stocks at a 1-year RSI extreme, watches for RSI to improve before price fully recovers, and enters only after both price and RSI confirm that the rebound is real.
