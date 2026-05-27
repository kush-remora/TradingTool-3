# Wyckoff Phase-1 Market Cap Threshold Calculation Fix

## Goal Description
Ensure the Wyckoff Phase-1 Scanner assigns the correct delivery threshold (market cap bucket) to a symbol based on its actual highest-threshold index membership in the database, regardless of which `universeKeys` were selected for the current scan run.

## Proposed Changes
1. `core/src/main/kotlin/com/tradingtool/core/indexconstituents/dao/IndexConstituentReadDao.kt`:
   - Added a `listAllActive()` method to fetch all active index memberships from `index_constituents` table. This provides a global view of all symbol memberships.
2. `core/src/main/kotlin/com/tradingtool/core/strategy/wyckoff/phase1/WyckoffPhase1ScannerService.kt`:
   - Updated `resolveUniverse()` to fetch all active memberships using `listAllActive()` and create a global `allMembershipBySymbol` lookup.
   - Applied `resolveMembershipByHighestThreshold()` using this global lookup for all seed symbols. This ensures a symbol like `CASTROLIND` receives its 70% `SMALLCAP` threshold even if the run was triggered with only `WATCHLIST`.
3. `frontend/src/pages/WyckoffPhase1Page.tsx`:
   - Added `index_key` to the default table columns to ensure it is visible by default in the frontend UI.

## Task List
- [x] Create `tasks/todo.md` with plan and checkable items
- [x] Update `IndexConstituentReadDao.kt` to add `listAllActive()`
- [x] Update `WyckoffPhase1ScannerService.kt` to use global index memberships via `listAllActive()`
- [x] Update `WyckoffPhase1Page.tsx` default columns to include `index_key`
- [x] Run backend build (`mvn clean compile`) and verify
- [x] Add Review Section

## Review Section
- The application was verified successfully via Maven (`mvn clean compile`), ensuring the addition of `listAllActive()` to `IndexConstituentReadDao.kt` doesn't break compilation.
- The correct threshold mapping is now applied to all scanned symbols using the global data.
- The UI defaults now display the `index_key` so the used market-cap bucket is transparent.
