# Hot SMA Pullback Scanner (Index-Key Hot Stocks)

## Goal Description
Create a simple Hot SMA scanner for index-key hot stocks with three signals (Aggressive/Standard/Watch), row-level Telegram send, and CSV export of filtered/sorted results.

## Task List
- [x] Add backend Hot SMA models and scanner service under `core/.../strategy/hotsma`
- [x] Add strategy APIs for universes/run/telegram in `StrategyResource`
- [x] Add request validation tests for Hot SMA APIs
- [x] Add core unit tests for SMA low-touch and signal priority rules
- [x] Add frontend types and `useHotSmaScanner` hook
- [x] Add `HotSmaScannerPage` with index select, filters, sorting, Telegram send, and CSV export
- [x] Wire new page into `App.tsx` menu/routes
- [x] Add frontend page test for run/filter/export flow
- [x] Run required backend/frontend tests
- [x] Run Kotlin reviewer gate and document findings
- [x] Add Review Section

## Review Section
### What was implemented
1. Added a new Hot SMA scanner domain under `core` with explicit models and scanner logic:
   - signal rules: `AGGRESSIVE_BUY` (SMA200 low touch, last 5), `STANDARD_BUY` (SMA100 low touch, last 5), `WATCH_ZONE` (0% to +10% above SMA200)
   - SMA50/SMA100/SMA200 from close
   - SMA200 uses available history (`min(availableBars, 200)`)
   - RSI14 included in output
2. Added APIs under `StrategyResource`:
   - `GET /api/strategy/hot-sma/universes`
   - `POST /api/strategy/hot-sma/run`
   - `POST /api/strategy/hot-sma/telegram`
3. Added frontend flow:
   - new `HotSmaScannerPage`
   - single index select, run action, filter chips, sort selector
   - row-level Telegram send
   - CSV export for the currently filtered/sorted rows
4. Added tests:
   - core unit tests for touch logic/signal precedence/watch-zone bounds/partial-history SMA200
   - resource validation tests for request normalization and required fields
   - frontend page test for run + filter + CSV export

### Verification
1. `mvn -pl core -Dtest=HotSmaScannerServiceTest test` ✅ passed
2. `mvn -pl resources -Dtest=HotSmaRequestValidationTest test` ⚠️ fails in this repo because `resources` module alone does not resolve required cross-module classes (existing workspace condition)
3. `mvn -pl resources -am -Dtest=HotSmaRequestValidationTest -Dsurefire.failIfNoSpecifiedTests=false test` ✅ build success (reactor mode)
4. `npm --prefix frontend run test:run -- HotSmaScannerPage` ✅ passed
5. `npm --prefix frontend run build` ✅ passed

### Kotlin Reviewer Gate
- Review scope: `HotSmaModels.kt`, `HotSmaScannerService.kt`, `ServiceModule.kt`, `StrategyResource.kt`, and new Kotlin tests.
- Findings:
  - CRITICAL: 0
  - HIGH: 0
  - MEDIUM: 0
  - LOW: 0
- Verdict: PASS
