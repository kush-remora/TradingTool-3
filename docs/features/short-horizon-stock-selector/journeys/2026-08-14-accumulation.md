# Accumulation super-tab

## Why

Kush wanted accumulation context visible while selecting stocks for a one-to-five-day trade. A separate console would split the workflow, so the first version lives as an Accumulation tab inside 5-Day Stock Selector.

## Implemented

- Added an Accumulation super-tab using the existing watchlist daily OHLCV response; no backend endpoint or migration was needed.
- Added independently sortable 30-session counts for:
  - buying-interest closes: close at least 70% up the daily range;
  - green closes: close above the previous close;
  - quiet moves: absolute close-to-close change below 1%;
- Added visible 5D and 20D close-to-close move columns for the latest stock price context.
  - volume dry-up: volume below the prior 10-session average.
- Added a compact 20-session four-line heatmap: Buy, Green, Quiet, and Vol.
- Heatmap dot hover text uses a compact weekday/date format, such as `Thu, 30 Jul 87.9%`, with a small translucent white tooltip.
- Added `Filter 1`, which keeps only stocks meeting all three requested conditions: Buy-interest ≥10 / 30, Quiet ≥8 / 30, and Volume dry-up ≥6 eligible sessions.
- Added `Filter 2` as a refinement of Filter 1: at least 3 of the latest 5 sessions must be buying-interest days, at least 3 must be green closes, and the total 5-session move must be at least +5%.
- Defaulted sorting to buying-interest days, with no combined score or buy signal.
- Added focused calculation and page-flow coverage.

## Decisions and tradeoffs

- Counts use the latest 30 trading sessions; heatmaps use the latest 20.
- The volume baseline excludes the classified day and requires 10 prior sessions. Its denominator shows only sessions with an available baseline.
- Flat candles have no valid close-location classification and appear as unavailable rather than being treated as buyer strength.
- The tab is descriptive Wyckoff evidence only. Delivery, support/base context, event labels, and trade recommendations remain out of scope.

## Validation

- Accumulation utility and selector page tests: passed, 4 tests.
- Frontend production build: passed.
- Full frontend suite: 191 passed, 7 failures in existing timeout/stale Compact Stock Review assertions; the accumulation utility test passed in the full run, while the selector page test passed in isolation but timed out under the full-suite load.

## Next follow-ups

- Review the live heatmap density and whether the four short labels are immediately understandable.
- Later, validate this descriptive lens against historical Wyckoff accumulation examples before adding any ranking score or rule-based filtering.
