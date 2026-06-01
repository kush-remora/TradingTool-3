# Journey: Hot SMA Pullback Scanner

## Feature
Hot SMA Pullback Scanner (index-key hot stocks) with manual Telegram reminder and CSV export.

## Why it was built
The goal is to quickly shortlist explosive/hot stocks already grouped by `index_key`, then focus on pullback entries around SMA100/SMA200 with minimal screen clutter.

## What was implemented
1. Backend scanner module under `core/src/main/kotlin/com/tradingtool/core/strategy/hotsma`.
2. Signal rules:
   - `AGGRESSIVE_BUY`: daily low touched SMA200 in last 5 sessions.
   - `STANDARD_BUY`: daily low touched SMA100 in last 5 sessions.
   - `WATCH_ZONE`: current price in 0% to +10% above SMA200.
3. Indicator outputs per stock:
   - current price, SMA50/SMA100/SMA200, % distance to each SMA, RSI14.
4. API endpoints:
   - `GET /api/strategy/hot-sma/universes`
   - `POST /api/strategy/hot-sma/run`
   - `POST /api/strategy/hot-sma/telegram`
5. Frontend page `HotSmaScannerPage`:
   - single index selector, scan button, signal filter chips, sorting options.
   - row-level Telegram send.
   - CSV export from currently visible (filtered + sorted) rows.

## Key decisions and tradeoffs
1. Kept v1 to single `indexKey` selection for simplicity and reduced UX complexity.
2. Used available-history SMA200 for partial-history names instead of excluding them.
3. Chose manual Telegram send only (no scheduler/auto-alert) to preserve control and reduce false reminder noise.

## Validation and outcomes
1. `mvn -pl core -Dtest=HotSmaScannerServiceTest test` passed.
2. `npm --prefix frontend run test:run -- HotSmaScannerPage` passed.
3. `npm --prefix frontend run build` passed.
4. `mvn -pl resources -Dtest=HotSmaRequestValidationTest test` fails when running `resources` in isolation due existing cross-module resolution behavior in this workspace.
5. `mvn -pl resources -am -Dtest=HotSmaRequestValidationTest -Dsurefire.failIfNoSpecifiedTests=false test` passed in reactor mode.

## Next follow-ups
1. Add optional multi-index scan mode if shortlist needs broader coverage.
2. Add optional user-defined watch-zone width (default still 10%).
3. Add quick link from row to stock detail screen for deep-dive workflow.
