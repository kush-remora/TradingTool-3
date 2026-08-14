# Short-Horizon First-Seen Date

## Why

When a stock remains in a filtered tab for multiple days, the review needs to show when it first appeared in the recent window so the user can connect today’s view with an earlier review.

## Implemented

- Replayed the existing All Stocks, Shortlist, Best aligned, and Latest 2-day finish rules at each of the last five completed session dates.
- Recorded the earliest date in that window when each stock belonged to each tab; shortlist history preserves its current acceleration filter and ranking path.
- Added `First seen DD Mon` above each symbol in the Stock cell across all tabs.
- Added `Close ±x.x%` from the first-seen close to the latest close and `High ±y.y%` from the first-seen close to the highest later-session high; the high date is available on hover.
- Styled each return independently: positive values are green, negative values are red, and positive returns of at least 5% are bold.
- Extended neutral `Volume activity` from a three-session to a five-session rolling window so a recent event remains visible during the following review week.
- Added a `Fresh today` tab after `Latest 2-day finish`, showing only stocks that entered the final tab in the current completed session.
- Reworked the All Stocks movement cell into a compact five-day result and path summary: direction, magnitude band, green-day count, daily G/R strip, and average daily change.
- Added a display-only stage label to that first-tab summary for every stock: `Fresh`, `Review`, or `Extended`, based on the latest close's distance above the recent 20-session low.
- Set the stage boundaries to `Fresh` through +10%, `Review` through +20%, and `Extended` above +20%; the existing 20D net movement stays only in `Latest close` context and is no longer a first-tab filter.
- Moved the stage label into `Latest close`; `Move now` is now limited to latest-5-session movement and no longer exposes the old acceleration enum filter.
- Added `Prior 5D` and a compact pace label to `Move now`: `Accelerating`, `Recovering`, `Steady`, or `Slowing`; the comparison uses a strict ±1 percentage-point band.
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
