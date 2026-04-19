# Weekly Buy Zone Review: Monday-Low Median of Bottom 3 (Last 10 Weeks)

Date: 2026-04-18
Status: Review only (no implementation in this change)

## Why This Review
You requested a change in buy-zone calculation:
- Use Monday lows from the last 10 completed weeks.
- Pick the 3 lowest Monday lows.
- Take their median.

This document explains current behavior, your proposed behavior, impact, and feedback before implementation.

## Current Behavior (As-Is)
Current backend logic computes buy zone from the selected `buyDay` (Mon/Tue/Wed etc.):
1. Take last `buyZoneLookbackWeeks` completed weeks (default 15).
2. Collect low prices for that selected `buyDay` only.
3. `buyDayLowMin` = minimum of that sample.
4. `buyDayLowMax` = maximum of that sample.

Then frontend zone status uses:
- `Below`: `LTP < buyDayLowMin`
- `Inside`: `buyDayLowMin <= LTP <= buyDayLowMax`
- `Above`: `LTP > buyDayLowMax`

## Requested Behavior
New anchor formula requested:
1. Take last 10 completed weeks.
2. Read Monday low for each week.
3. Sort Monday lows ascending.
4. Take the 3 smallest values.
5. Median of those 3 values = robust floor.

Example:
- Monday lows (10w): `100, 104, 98, 103, 101, 99, 97, 106, 102, 105`
- 3 lowest: `97, 98, 99`
- Median = `98`

## Key Product Decision Needed Before Coding
Your formula produces one value (robust floor), but zone currently needs a range (`min`, `max`).

We should decide one of these options before implementation:
1. Recommended: keep range model and change only floor.
   - `buyDayLowMin = median(bottom3 Monday lows from last 10w)`
   - `buyDayLowMax = max Monday low from last 10w`
2. Narrow band model:
   - `buyDayLowMin = min(bottom3)`
   - `buyDayLowMax = max(bottom3)`
3. Single-point model (not recommended):
   - `buyDayLowMin = buyDayLowMax = median(bottom3)`

Feedback: Option 1 is the most practical with minimal UI and scoring disruption.

## Behavioral Implications
If we switch from buy-day lows to Monday-only lows:
- Zone becomes more stable and less sensitive to one-off spike lows.
- Zone may become inconsistent with labels like "Hist. Tue low range" when buy day is Tue.
- Pattern logic still chooses buy day dynamically, but zone source becomes fixed to Monday unless we explicitly rename UI text and API semantics.

## Risk Review
1. Semantic mismatch risk
- Current fields are named `buyDayLowMin/Max`, but calculation becomes Monday-specific.
- Suggested mitigation: rename in API later (non-breaking migration) or add metadata flag `buyZoneSourceDay = MON`.

2. Data sparsity risk
- Holidays can reduce Monday samples below 10.
- Suggested fallback:
  - If <3 Monday samples: median of available Monday samples.
  - If no Monday samples: fallback to existing buy-day logic.

3. UI communication risk
- Current detail card text says `Avg {buyDay} low range`.
- If Monday-only logic is adopted, text should say `Monday low-derived zone`.

## Technical Touchpoints (When We Implement)
Backend:
- `core/src/main/kotlin/com/tradingtool/core/screener/WeeklyPatternService.kt`
  - `calculateBuyDayLowRange(...)`
  - both call sites in list and detail flow

Frontend copy updates:
- `frontend/src/components/ScreenerDetail.tsx` (`Avg {buyDay} low range...` label)
- optional explanatory tooltip in `ScreenerOverview.tsx`

Tests to add/update:
- Unit tests for Monday-low median(bottom3) logic.
- Edge cases (<3 Monday samples, no Monday data).
- Regression tests for zone status and near-zone filter behavior.

## Recommended Rollout Plan
1. Finalize decision for zone upper bound (Option 1/2/3).
2. Implement backend formula with safe fallback behavior.
3. Update UI text so source day is explicit.
4. Run compare report (old vs new zone status counts over recent symbols).
5. Enable after review sign-off.

## Final Feedback
The requested formula is sensible and more robust than simple raw minimum.

Best path:
- Adopt Monday-low median(bottom3 of last 10 completed weeks) as robust floor.
- Keep a practical upper bound for range (recommended: max Monday low in same 10-week window).
- Update labels so users understand this is Monday-derived, not buy-day-derived.

No code implementation has been made in this request.
