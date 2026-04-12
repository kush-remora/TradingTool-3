# Remora Philosophy

## Why We Chose The Name "Remora"

The Remora fish does not hunt on its own. It attaches itself to a larger fish and feeds from what that larger fish leaves behind.

That is the exact philosophy of this strategy.

We are not trying to out-research institutions.
We are not trying to predict what they will do before the market shows evidence.
We are not trying to invent a story around every stock.

Instead, we look for the footprints left by institutional activity:

- unusually strong delivery,
- unusual participation,
- muted price movement while buying may be happening,
- and later, the public breakout that confirms interest is becoming visible.

Our edge is not "finding food first."
Our edge is recognizing that a larger, more informed participant was already interested, then positioning ourselves after the footprint is visible and the breakout confirms it.

## Strategy Intuition

In plain language:

1. An institution does the deep research and begins building interest in a stock.
2. That interest leaves traces in market data.
3. Remora looks for those traces.
4. We do not buy just because a footprint appeared.
5. We wait for the breakout.
6. Once breakout happens after earlier institutional footprint, we join the move.

This is a "follow the informed money, but wait for confirmation" system.

## What This Means For Implementation

This philosophy should shape every major change in the repo.

### Backend

- Backend logic should be organized around detecting institutional footprints first, not around generic technical signals.
- Raw inputs that help identify institutional participation are first-class data:
  - delivery percentage,
  - delivery ratio,
  - volume anomalies,
  - price compression,
  - breakout confirmation,
  - and selected stock-health filters.
- Data models and services should preserve traceability so we can explain why a stock looked institutionally interesting.

### UI

- The UI should not feel like a raw-data dump.
- Every screen should help answer:
  - "Did we detect an institutional footprint?"
  - "How strong was it?"
  - "Is breakout confirmation present yet?"
  - "What is missing before this becomes actionable?"
- Visual hierarchy should emphasize:
  - footprint strength,
  - confirmation state,
  - readiness,
  - and reasons for disqualification.

### Product Decisions

- We prefer signals that reflect informed participation over noisy standalone indicators.
- We prefer confirmation over guessing bottoms.
- We prefer explainable signals over black-box scores.
- We prefer workflows that help us attach to institutional intent, not compete with it.

## Non-Negotiable Design Principle

Remora is not just a strategy label.
It is the core product philosophy for this part of the tool.

When building or refactoring related features, ask:

- Are we exposing institutional footprint clearly?
- Are we separating footprint detection from breakout confirmation?
- Are we helping the user act only after evidence, not before?
- Does this screen or backend flow make the Remora philosophy more visible or less visible?

If a change weakens that clarity, it is probably the wrong change.
