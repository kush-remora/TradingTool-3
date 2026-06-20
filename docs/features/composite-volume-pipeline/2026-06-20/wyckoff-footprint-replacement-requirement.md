# Requirement: Wyckoff Footprint Replacement

## Problem Statement

The current Wyckoff requirement direction is spread across older Phase 1 logic, Remora-style accumulation ideas, and newer notes that are materially stricter. That makes the current strategy hard to trust, hard to tune, and too easy to drift back into generic scanner behavior.

## Goal

Replace the current Wyckoff requirement with a footprint-first pipeline that detects institutional absorption through lagged structural context, delivery intensity, volatility compression, and density. The new requirement should identify Phase C style readiness without collapsing breakout confirmation into the same step.

## User Story

As a trader, I want the Wyckoff scanner to tell me when a stock is showing credible seller exhaustion and institutional absorption near its base, so I can wait for a later confirmation trigger instead of guessing.

## Product Decision

- Deprecate the current Wyckoff requirement direction as the product source of truth.
- Keep the `Remora` name for the replacement strategy surface.
- Keep breakout confirmation as a later state, not part of the raw accumulation-footprint trigger.
- Keep the logic configurable by market-cap tier.

## Acceptance Criteria

1. The replacement scanner must use a lagged structural basement:
   - calculate a closing-price basement threshold from the prior `lookback_period_days`, excluding today,
   - isolate the cheapest `basement_percentile` of the lagged window.
2. The scanner must calculate a lagged delivery baseline only from basement-price sessions:
   - use median delivery quantity, not mean,
   - apply `min_liquidity_floor_dq` as a hard denominator floor.
3. The scanner must calculate an `accumulation_spike` for today using the lagged delivery baseline.
4. The scanner must calculate `spread_squeeze_pct` using today's true range vs a lagged 20-day average volatility baseline.
5. The scanner must calculate `distance_to_200sma_pct` and gate candidates with configurable lower and upper bounds.
6. The final footprint trigger must require all of the following:
   - `spread_squeeze_pct` passes,
   - `distance_to_200sma_pct` is inside the allowed range,
   - `accumulation_spike` is above the configured minimum,
   - the density count of squeeze-plus-context days inside `density_rolling_window_days` is at least `density_qualification_days`.
7. The output must expose the component metrics, not just a boolean:
   - basement threshold price,
   - wholesale base delivery,
   - accumulation spike,
   - spread squeeze,
   - 200 SMA distance,
   - density count,
   - final trigger state.
8. The scanner must preserve the Remora philosophy:
   - detect institutional footprint first,
   - do not treat the footprint alone as a buy signal,
   - separate footprint detection from breakout confirmation.

## Suggested State Model

- `SCOUTING`: no footprint yet
- `FOOTPRINT_FORMING`: some conditions present but density not complete
- `FOOTPRINT_CONFIRMED`: Phase C style exhaustion detected
- `WAITING_FOR_BREAKOUT`: confirmed footprint remains valid, but no public trigger yet
- `DISQUALIFIED`: context broke or the structure failed

## Technical Considerations

- Configuration should be per market-cap tier, at minimum `LARGE`, `MID`, and `SMALL`.
- The requirement should replace current decision logic, but code migration can happen in stages so existing flows are not deleted blindly.
- This spec should be the anchor for any future Phase D confirmation logic or UI redesign.
- Current `Remora` and `core/.../strategy/wyckoff/phase1` logic should be reviewed against this requirement during migration planning.

## Out of Scope

- Final trade execution rules.
- Intraday entry tactics.
- Telegram/alert wording.
- Full frontend layout specification.

## Complexity Estimate

3-5 days for requirement-to-design translation and migration planning before implementation starts. Implementation effort will be larger because current logic is being replaced, not lightly patched.
