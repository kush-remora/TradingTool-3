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

---

# Implementation Plan: Strategy Foundations - Delivery Data + Fundamental Health

## Overview
Build the raw data foundations for the Remora strategy before touching final strategy logic. Remora is the institutional-footprint strategy we want to complete, and the eventual breakout layer will extend this base rather than replace it. The first two independent tracks are:

1. Delivery data integration using bhav copy or another trustworthy daily delivery source as the source of truth
2. Fundamental health metrics storage for quality filtering

These tracks should land as reusable data pipelines and Postgres-backed datasets that future strategies can query cleanly.

## Problem
- Current delivery support is tied to one existing NSE-based path and was added for the earlier half-built Remora implementation.
- Kite delivery data is not reliable enough for this strategy.
- Fundamental quality data is not modeled in the backend yet.
- Without these raw inputs, the final strategy would rest on incomplete or misleading signals.

## Goals
- Replace or redesign the current delivery ingestion path around a trustworthy daily delivery source such as bhav copy.
- Persist delivery data in Postgres with all useful raw fields required for future analysis and debugging.
- Persist stock-level fundamental health metrics such as P/E ratio and related quality indicators.
- Keep both pipelines independent so they can be validated before strategy rules depend on them.

## Assumptions
- Bhav copy or another trustworthy daily delivery source will be the canonical delivery input in V1.
- Initial stock scope is:
  - Nifty large-cap universe
  - Nifty mid-cap universe
  - all current watchlist stocks
- We should store raw source metadata for auditability instead of only derived fields.
- Fundamentals do not need intraday freshness in V1; daily or slower refresh is acceptable.

## Architecture Direction

### Track A: Delivery Data Integration
- Add a dedicated delivery ingestion service for bhav copy or another trusted delivery dataset.
- Keep source parsing separate from persistence logic.
- Preserve raw source attributes alongside normalized fields.
- Do not bury source-specific ingestion logic inside Remora.
- Remora should read delivery data from a stable repository/service layer, not from source adapters directly.

### Track B: Fundamental Health Metrics
- Add a separate fundamentals module with its own fetch, normalize, and persist flow.
- Start with a small, explicit metric set:
  - P/E ratio
  - market cap
  - debt/equity
  - pledged shares percentage
  - promoter holding if source supports it
  - return on equity / return on capital only if source quality is consistent
- Store both metric values and freshness metadata.

## Suggested Build Order

### Phase 1: Delivery foundation
- [ ] Decide the canonical delivery source for V1 and inspect its exact format and stable columns.
- [ ] Define the stock scope builder for large-cap, mid-cap, and watchlist symbols.
- [ ] Design the normalized Postgres schema for delivery history plus source metadata.
- [ ] Implement the delivery parser/ingestion service.
- [ ] Add idempotent upsert flow into Postgres.
- [ ] Add a read path for latest delivery history per stock.
- [ ] Verify delivery coverage and freshness for the target universe.

### Phase 2: Fundamental foundation
- [ ] Choose the fundamentals source and document refresh expectations.
- [ ] Define a minimal health-metrics schema in Postgres.
- [ ] Implement fetch + normalize + persist flow.
- [ ] Add a read path for latest metrics per stock.
- [ ] Verify coverage, null handling, and stale-data behavior.

### Phase 3: Integration readiness
- [ ] Refactor Remora and future strategy code to depend on repository/service interfaces instead of source-specific logic.
- [ ] Add operational checks for stale delivery and stale fundamentals.
- [ ] Add minimal inspection endpoints/UI only if needed for debugging.
- [ ] Record final readiness criteria before starting the Accumulation -> Breakout strategy layer.

## Delivery Data Design Notes
- Prefer a new source-agnostic delivery service over extending `NseDeliverySourceAdapter`.
- Keep the parser resilient to column-order changes if the chosen delivery file format is semi-manual.
- Store enough raw values to recompute delivery percentage or validate anomalies later.
- Preserve `source_name`, `source_file_name`, `source_imported_at`, and any source date field available.
- Idempotency should be based on stock + trading date + source precedence, not import time.

## Fundamental Data Design Notes
- Start with one row per stock per refresh date or one latest snapshot row plus optional history table.
- Prefer clarity over a generic EAV schema.
- Keep the initial metric list small and directly tied to strategy filters.
- Missing metrics should be explicit nulls, not silent defaults.

## Risks & Mitigations
- Risk: chosen delivery source format is semi-manual or inconsistent.
  - Mitigation: Build parser validation and reject malformed imports loudly.
- Risk: Current delivery table is too narrow for future analysis.
  - Mitigation: review schema before implementation and expand intentionally rather than patching later.
- Risk: Fundamentals source quality varies by metric.
  - Mitigation: start with a minimal metric set and mark per-field freshness/source.
- Risk: Strategy code couples directly to one source.
  - Mitigation: introduce a source-agnostic service/repository boundary now.

## Immediate Next Task
- [ ] Finalize the delivery-data ingestion design around bhav copy or the selected trusted source, including:
  - target schema
  - import workflow
  - stock-universe scope builder
  - refresh/idempotency rules

## Success Criteria
- [ ] Delivery history for the target universe is available in Postgres from the chosen trusted delivery source.
- [ ] Fundamental health metrics exist in Postgres with freshness metadata.
- [ ] Both datasets are queryable independently of any strategy implementation.
- [ ] Remora can consume these inputs without knowing their original source.

## Review
- Planning only so far.
- Current repo already contains NSE-based delivery ingestion and a delivery history table, but it is tailored to the existing half-built Remora path and should be treated as a starting point, not the final foundation.
- Corrected earlier planning mistake: the target is not Power Copy specifically; it is bhav copy or another trustworthy daily delivery source.
- No code implementation started yet for redesigned delivery ingestion or fundamentals.
