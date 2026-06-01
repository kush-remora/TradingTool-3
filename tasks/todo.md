# Instrument Token Resolver - NSE `-IV` Fallback

## Goal Description
Handle additional NSE symbol resolution case where app symbol is base form (for example `PGINVIT`) and Kite tradingsymbol is suffixed form (`PGINVIT-IV`).

## Task List
- [x] Inspect resolver behavior for NSE suffix fallbacks
- [x] Implement minimal fallback support for NSE `-IV`
- [x] Add focused unit test for `PGINVIT -> PGINVIT-IV`
- [x] Run relevant Kotlin test
- [x] Run Kotlin reviewer pass and document result
- [x] Add Review Section

## Review Section
### Confirmed issue found
1. Resolver only had explicit NSE fallback for `-BE`, so valid instruments like `NSE:PGINVIT-IV` were unresolved when queried as `PGINVIT`.

### Fixes applied
- Added NSE fallback suffixes list in `InstrumentTokenResolverService`: `-BE`, `-IV`.
- Resolver now tries each fallback suffix in order and returns first match.
- Updated expected key diagnostics to include `NSE:<SYMBOL>-IV`.
- Added unit test to confirm `PGINVIT` resolves to `PGINVIT-IV` token.

### Verification
- `mvn -pl core -Dtest=KiteInstrumentTokenResolverTest test` passed.

### Kotlin Reviewer Gate
- No CRITICAL/HIGH/MEDIUM/LOW issues found in this minimal change set.
