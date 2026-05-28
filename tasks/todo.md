# Intraday Volume Shock Review & Fix

## Goal Description
Review Intraday Volume Shock implementation, identify concrete issues across backend/frontend behavior, and apply minimal, readable fixes.

## Task List
- [x] Verify request/response contract consistency between backend and frontend
- [x] Verify strategy rule implementation against requirements doc
- [x] Fix confirmed issues with minimal diffs
- [x] Run frontend/backend compile checks
- [x] Run Kotlin review gate and add findings
- [x] Add Review Section

## Review Section
### Confirmed issues found
1. Morning scan window was inclusive of the scan-end timestamp in backend (`<=` behavior), which incorrectly included the next 5m bar (for 9:15-9:30 this included 9:30 candle).
2. Frontend used `gapUpPct` while backend returns `gapPct`, so gap values were not mapped correctly.
3. Frontend trade `exitReason` type missed `EOD_FORCED`, which can be returned by backend.
4. Frontend response type was stale (missing `summary` and `diagnostics`), creating contract drift.

### Fixes applied
- Changed scan window check to end-exclusive (`bar.timestamp.isBefore(scanEndTarget)`).
- Aligned frontend trade field to `gapPct` and label to `Gap %`.
- Added `EOD_FORCED` to frontend `exitReason` union.
- Added `IntradayShockBacktestSummary` and `IntradayShockBacktestDiagnostics` interfaces and wired them into `IntradayShockBacktestResponse`.
- Updated Total Signals card to use backend summary (`data.summary.totalTrades`).

### Verification
- `mvn -q -DskipTests compile` passed.
- `npm run frontend:build` passed.
