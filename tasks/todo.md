# Structural Cleanup Implementation Plan

The massive deletion of deprecated workflows has left a few dangling references and broken imports across the frontend and backend. This plan details the structural updates required to make the codebase compile and function smoothly again.

## Proposed Changes

### [Backend] Dead Service Cleanup
- `[ ]` Delete `core/src/main/kotlin/com/tradingtool/core/strategy/volume/VolumeSpikeBacktestService.kt`
- `[ ]` Delete `core/src/main/kotlin/com/tradingtool/core/strategy/volume/VolumeSpikeBacktestModels.kt`
- `[ ]` Delete `core/src/main/kotlin/com/tradingtool/core/technical/TechnicalContextService.kt`
- `[ ]` Delete `core/src/main/kotlin/com/tradingtool/core/technical/TechnicalContextModels.kt`
*(These files were part of the deprecated technical scanners and backtests but were missed in the initial `rm -rf` sweeps).*

### [Backend] Decoupling from the "Stock Table"
We previously deleted the master `Stock` database table sync workflow because we no longer want to maintain a heavy generic stock database. Several services still rely on `StockJdbiHandler` or `StockService` and need to be adapted to use raw symbols or the `IndexConstituent` table instead:

- `[ ]` Modify `core/src/main/kotlin/com/tradingtool/core/trade/service/TradeService.kt`: 
  - Remove `StockService` dependency. 
  - When creating a trade, use the provided raw symbol directly instead of looking up the stock metadata in the DB.
  
- `[ ]` Modify `core/src/main/kotlin/com/tradingtool/core/trade/service/TradeReadinessService.kt`:
  - Remove `StockJdbiHandler` dependency.
  - Assume symbols are valid or rely on the Groww Watchlist directly.

- `[ ]` Modify `core/src/main/kotlin/com/tradingtool/core/strategy/wyckoff/phase1/WyckoffPhase1ScannerService.kt`:
  - Remove `StockJdbiHandler` dependency.
  - When resolving the "Track C" universe, do not fall back to `StockJdbiHandler.listAll()`. Instead, pull the universe purely from `IndexConstituentJdbiHandler` and/or the Groww watchlist adapter.

### [Frontend] Router Cleanup
- `[ ]` Modify `frontend/src/App.tsx`:
  - Remove imports for all deleted React pages (e.g. `BaseSwingPage`, `BollingerSqueezePage`, `FiftyTwoWeekHighBacktestPage`, etc.).
  - Remove these paths from the `V1PageKey` and `validPages` arrays.
  - Remove them from the `menuItems` sidebar configuration.
  - Remove them from the `<Layout.Content>` router switch.

## Verification Plan
### Automated Tests
- `[ ]` Run `cd frontend && npm run build` to verify the frontend transpiles cleanly without missing module errors.
- `[ ]` Run `mvn clean compile -DskipTests` to verify the backend Kotlin compiler is green and all dangling references to deleted services have been resolved.
