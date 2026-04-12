# Implementation Plan: S4 Tiered Volume Spike Multi-Cap Momentum Filter

## Overview
Build S4 as a reusable volume-analysis sub-strategy that can later feed the Accumulation + Breakout system. The first slice should add a shared core volume analyzer, an S4 strategy service over curated universes, a strategy API, and enough frontend visibility to inspect outputs.

## Requirements
- Reuse a shared volume-analysis core instead of burying logic inside one strategy.
- Run only on curated universes, not the entire exchange.
- Cover three profiles intended by the strategy idea: large-cap, mid-cap, and small-cap 250 style universes.
- Apply a liquidity guardrail similar to RSI momentum so illiquid names are excluded.
- Keep implementation simple, readable, and easy to extend into accumulation/breakout later.

## Assumptions
- For V1, use available curated universe files in repo where possible and keep the universe loader configurable.
- Use average traded value as the liquidity guardrail because that already exists in the codebase and is directly derivable from candles.
- S4 should expose ranked scanner output first, not auto-trading or persistence.

## Architecture Changes
- `core/src/main/kotlin/com/tradingtool/core/strategy/volume/` — shared volume analysis models and analyzer.
- `core/src/main/kotlin/com/tradingtool/core/strategy/s4/` — S4 config, models, and service.
- `resources/src/main/kotlin/com/tradingtool/resources/StrategyResource.kt` — add S4 endpoints.
- `service/src/main/kotlin/com/tradingtool/di/ServiceModule.kt` — register S4 service/config.
- `frontend/src/hooks/` and `frontend/src/pages/` — add S4 page and data hook.
- `tasks/todo.md` — track progress and review notes.

## Implementation Steps
- [ ] Review existing RSI momentum and Remora patterns to reuse config, universe, and API conventions.
- [ ] Add shared volume-analysis core models/analyzer for baseline volume, spike ratio, and price expansion metrics.
- [ ] Add S4 config + service that loads profile universes, computes candidates, applies liquidity filters, and returns ranked snapshots.
- [ ] Add strategy API endpoints for latest/refresh S4 snapshots.
- [ ] Add minimal frontend page to inspect S4 profiles and candidates.
- [ ] Add targeted tests for analyzer and ranking/filter behavior.
- [ ] Run relevant backend/frontend tests and record results.

## Testing Strategy
- Unit tests for shared volume analyzer calculations.
- Unit tests for S4 ranking and filter behavior.
- Frontend smoke test coverage only if page logic is complex enough to justify it.
- Build/test the touched modules only.

## Risks & Mitigations
- Risk: No dedicated large-cap 250 universe file exists in repo.
  - Mitigation: Keep universe loading configurable and document the current profile mapping used in V1.
- Risk: Volume-only spikes create noisy candidates.
  - Mitigation: Require both relative volume and positive price expansion; rank rather than auto-trade.
- Risk: Strategy logic drifts away from future accumulation use case.
  - Mitigation: Put reusable volume metrics in a shared core analyzer with neutral naming.

## Success Criteria
- [ ] Shared volume-analysis service exists in `core`.
- [ ] S4 returns profile-based ranked candidates from curated universes.
- [ ] Liquidity filtering excludes thin names.
- [ ] API is callable from frontend.
- [ ] Tests for new logic pass.

## Review
- Pending implementation.

---

# Bugfix Plan: Supabase Session Mode Max Clients

## Problem
Backend intermittently fails with:
`org.postgresql.util.PSQLException: FATAL: MaxClientsInSessionMode: max clients reached - in Session mode max clients are limited to pool_size`.

## Implementation Steps
- [x] Identify where database clients are created and why total sessions exceed session-mode pool limits.
- [x] Refactor DB initialization so all DAOs share one pooled `Jdbi` per JDBC URL.
- [x] Add conservative pool defaults and environment overrides for safe tuning.
- [x] Validate compilation/tests for touched modules.
- [x] Document outcome and residual risk in review section.

## Review
- Root cause confirmed: each DAO handler created independent DB clients, which could exceed Supabase Session mode `pool_size`.
- Fix applied: single shared pooled `Jdbi` per JDBC URL with HikariCP.
- Added env tuning knobs with safe defaults:
  - `SUPABASE_DB_MAX_POOL_SIZE` (default `5`)
  - `SUPABASE_DB_MIN_IDLE` (default `0`)
  - `SUPABASE_DB_CONNECTION_TIMEOUT_MS` (default `10000`)
  - `SUPABASE_DB_IDLE_TIMEOUT_MS` (default `600000`)
  - `SUPABASE_DB_MAX_LIFETIME_MS` (default `1800000`)
- Verification: `mvn -pl core,service -DskipTests compile` passed.
- Residual risk: if external workloads consume most DB sessions, this service can still hit limits; tune pool size to stay below your Session mode cap.
