# Short-Horizon First-Seen Date

## Why

When a stock remains in a filtered tab for multiple days, the review needs to show when it first appeared in the recent window so the user can connect today’s view with an earlier review.

## Implemented

- Replayed the existing All Stocks, Shortlist, Best aligned, and Latest 2-day finish rules at each of the last five completed session dates.
- Recorded the earliest date in that window when each stock belonged to each tab; shortlist history preserves its current acceleration filter and ranking path.
- Added `First seen DD Mon` above each symbol in the Stock cell across all tabs.
- Added `Close ±x.x%` from the first-seen close to the latest close and `High ±y.y%` from the first-seen close to the highest later-session high; the high date is available on hover.
- Recomputed the reference when the watchlist data or Shortlist filters change; no scanner thresholds or membership rules changed.

## Key decisions

- The reference is rolling and tab-specific. If a stock remains qualified tomorrow, its original date remains visible; if it is older than the five-session window, the reference naturally resets.
- The high return excludes the first-seen session's intraday high because the scanner is evaluated after that close and the metric is intended to calibrate a post-signal GTT target.
- All Stocks shows the start of the five-session window because every available stock is present by definition. Filtered tabs show actual first qualification.

## Validation

- `npm run test:run -- src/utils/shortHorizonSelector.test.ts src/pages/ShortHorizonSelectorPage.test.tsx`: 29 tests passed, including close/high return coverage.
- `npm run build`: passed; the existing Vite large-chunk advisory remains.
- `git diff --check`: passed.
- Code review found no critical or high-confidence issues.

## Next

- Use the new date reference during daily review to decide whether a stock is newly qualified or already reviewed in the current five-session window.
