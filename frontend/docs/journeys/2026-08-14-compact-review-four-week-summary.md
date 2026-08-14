# Compact Review 4W Summary — 2026-08-14

## Feature and why

Add a fast discovery tab beside the compact stock review so the user can find likely floor-alignment candidates without opening every watchlist stock one by one. A candidate is a stock whose current observed week's minimum is within 1% of the immediately preceding week's minimum.

## Implemented

- Added `Stock review` and `4W Summary` tabs to the compact stock review; stock review remains the default.
- Added all-watchlists-by-default scanning with multi-watchlist selection.
- Reused the existing weekly watchlist scan endpoint and deduplicated symbols across selected lists.
- Added typed latest-two-week low alignment calculation with inclusive 1% tolerance.
- Summary columns are stock, this-week low/date, last-week low/date, signed gap, and compact-review action.
- Added `view=summary` URL state and stock drill-down back to the compact review.

## Decisions and tradeoffs

The change remains frontend-only because the existing API already returns enough daily candles for the weekly calculation. The summary intentionally does not label a match as accumulation or a buy signal; it is a floor-watch queue that still requires the compact evidence review. The all-watchlists default matches the discovery workflow, while multi-select limits the scan when a narrower universe is useful.

## Validation

- Focused compact summary test passed.
- Focused latest two-week alignment utility test passed.
- Frontend production build passed.
- The broader compact-page test file retains two unrelated pre-existing stale assertions: paper-trade age and a removed header CSS class.

## Next follow-up

Review the candidate queue over several live weeks before deciding whether the 1% threshold or the all-watchlists default needs adjustment.
