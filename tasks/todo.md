# Wyckoff Phase-1 Scanner Symbol Source Removal

## Goal Description
Fix Wyckoff Phase-1 scanner so it no longer defaults to watchlist symbols. Remove symbol-source controls and run scanner only from selected `index_key` universe keys.

## Skill Invocation (Mandatory)
- [x] `coding-standards` invoked (readability-first, small diff)
- [x] `backend-architect` invoked (no backend contract change required)
- [x] `kotlin-patterns` invoked (no Kotlin implementation change required)
- [x] `frontend-patterns` invoked (UI/state simplification)
- [x] `kotlin-reviewer` queued as final review gate note (no Kotlin diff expected)

## Task List
- [x] Remove symbol-source UI and state from Wyckoff Phase-1 page
- [x] Make run payload universe-key-only from multiselect
- [x] Remove now-unused frontend symbol-source type
- [x] Update Wyckoff Phase-1 frontend tests for new behavior
- [x] Run targeted frontend test and build verification
- [x] Add feature journey doc for today
- [x] Add Review Section outcomes

## Review Section
### What was implemented
1. Simplified `WyckoffPhase1Page` control model to universe-only selection:
   - Removed `Symbol Source` radio options and dependent symbol selectors.
   - Removed watchlist symbol loading/state from this page.
   - Removed `Selected symbols` indicator.
2. Scanner run payload now always sends:
   - `universeKeys` from the multiselect.
   - `applyStrictBaseFilter` as before.
   - no `symbols` override by default.
3. Updated persisted filter handling:
   - Store only `universeKeys` and `strictBaseFilter` for this screen.
4. Updated frontend test coverage to match new UX behavior.
5. Removed unused `WyckoffPhase1SymbolSourceMode` type.

### Verification
1. `npm --prefix frontend run test:run -- WyckoffPhase1Page` ✅ passed (4 tests)
2. `npm --prefix frontend run build` ✅ passed

### Kotlin Reviewer Gate
- Kotlin reviewer invocation acknowledged per policy.
- Kotlin/KTS diff scope check: none (`git diff --name-only | rg '\.(kt|kts)$'` returned empty).
- Verdict: PASS (no Kotlin-related implementation in this change).
