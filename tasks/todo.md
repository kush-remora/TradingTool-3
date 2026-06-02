# Wyckoff Phase-1 UI Column Filters

## Goal Description
Add per-column filtering to the Wyckoff Phase-1 result table and make JSON export respect the filtered rows.

## Skill Invocation (Mandatory)
- [x] `coding-standards` invoked (simple, readable UI state)
- [x] `backend-architect` invoked (no backend/API change needed)
- [x] `kotlin-patterns` invoked (no Kotlin implementation change in this slice)
- [x] `frontend-patterns` invoked (table/filter UX and state behavior)
- [x] `kotlin-reviewer` acknowledged (no Kotlin/KTS diff in this slice)

## Task List
- [x] Add visible filter inputs for each enabled table column
- [x] Apply filters to the rendered Phase-1 rows
- [x] Export only the filtered rows
- [x] Add focused frontend test coverage
- [x] Run targeted frontend verification
- [x] Add feature journey note for today
- [x] Add Review Section outcomes

## Review Section
### What was implemented
1. Added lightweight filter inputs above the Phase-1 result table for every visible column.
2. Applied local filtering to the current result set before rendering the table.
3. Updated export so JSON contains only the currently filtered rows.
4. Added a filtered row-count summary and a `Clear Filters` action.
5. Added a focused page test proving Symbol filtering narrows visible rows.

### Verification
1. `npm --prefix frontend run test:run -- WyckoffPhase1Page` ✅ passed
2. `npm --prefix frontend run build` ✅ passed

### Kotlin Reviewer Gate
- No Kotlin/KTS files changed in this slice.
- Verdict: PASS

# Kite Startup Token Validation

## Goal Description
Prevent `KiteTicker` from attempting a WebSocket handshake with an expired `kite_tokens` entry at startup.

## Skill Invocation (Mandatory)
- [x] `coding-standards` invoked (keep the fix small and readable)
- [x] `backend-architect` invoked (fix startup auth boundary before market services start)
- [x] `kotlin-patterns` invoked (small Kotlin helper + explicit validation flow)
- [x] `frontend-patterns` invoked (no direct UI change in this slice)
- [x] `kotlin-reviewer` review gate completed
- [x] `code-reviewer` review completed

## Task List
- [x] Confirm stale-token startup failure path
- [x] Add explicit Kite session validation after DB token load
- [x] Keep token normalization at the auth boundary
- [x] Add focused startup validation regression test
- [x] Run focused service/core tests
- [x] Add today's journey note for this fix
- [x] Add Review Section outcomes

## Review Section
### What was implemented
1. Added `KiteConnectClient.validateSession()` so startup can prove the persisted token still works before market services boot.
2. Normalized access tokens with `trim()` at apply-time and cleared cached token state when Kite marks the session expired.
3. Added a small startup helper in `service` to fail fast with a clear expired-token message instead of reaching a `KiteTicker` WebSocket `403`.
4. Added focused regression tests for core token normalization/state handling and for the service startup helper.

### Verification
1. `mvn -pl core -Dtest=KiteConnectClientTest test` ✅ passed (`core/target/surefire-reports/com.tradingtool.core.kite.KiteConnectClientTest.txt`)
2. `mvn -q -pl service -Dtest=KiteStartupTokenValidationTest,ApplicationTest test` ⚠️ blocked by existing service compile errors:
   - `service/src/main/kotlin/com/tradingtool/di/ServiceModule.kt` unresolved `hotsma` / `HotSmaScannerService`
   - initial `validateSession` unresolved was from running `service` without `-am`, then the build stopped on the pre-existing `HotSma` errors

### Kotlin Reviewer Gate
- Kotlin review findings: no blocking coroutine, lifecycle, or architecture issues in the auth-validation change.
- Verdict: PASS

### Code Reviewer Gate
- Code review findings: no critical or high-confidence blocking issues on the diff.
- Verdict: APPROVE
