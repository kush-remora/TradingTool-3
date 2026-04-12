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

# Implementation Plan: Config-Driven Delivery Ingestion Foundation

## Overview
Implement the delivery-data foundation for Remora using a dedicated JSON config, a config-driven V1 universe, and an `instrument_token`-aware delivery table/model path.

## Implementation Steps
- [x] Add a dedicated `delivery_config.json` and Kotlin config models/service.
- [x] Lock V1 delivery universe to `NIFTY_LARGEMIDCAP_250 + NIFTY_SMALLCAP_250 + NSE watchlist`.
- [x] Add a delivery universe service so delivery flows stop borrowing RSI config.
- [x] Extend `stock_delivery_daily` schema, constants, model, and DAO upsert/read mapping with `instrument_token`.
- [x] Refactor delivery validation and Remora delivery persistence to use the delivery config universe.
- [x] Run focused tests and compile checks.
- [x] Add final review notes.

## Review
- Added `delivery_config.json` and a dedicated delivery config/universe service so delivery scope is now config-driven instead of borrowed from RSI settings.
- Locked V1 delivery universe to `NIFTY_LARGEMIDCAP_250 + NIFTY_SMALLCAP_250 + NSE watchlist`, with watchlist filtering limited to NSE stocks.
- Extended `stock_delivery_daily` with `instrument_token`, backfill SQL, and a new `(instrument_token, trading_date)` index while preserving the existing `(stock_id, trading_date)` primary key.
- Refactored delivery validation and Remora persistence to consume the new delivery universe service.
- Hardened delivery row mapping with a timestamp fallback when `OffsetDateTime` retrieval is not supported by the JDBC result set implementation.
- Verification:
  - `mvn -q -pl core -Dtest=DeliveryConfigServiceTest,DeliveryUniverseServiceTest,StockDeliveryMapperTest,NseDeliverySourceAdapterTest,DeliverySourceValidationAnalyzerTest test`
  - `mvn -q -pl core,service,cron-job -DskipTests compile`
- Residual note: the schema change relies on backfilling `instrument_token` from `stocks` before enforcing `NOT NULL`, so the live database should be checked once after applying `tables.sql`.

---

# Implementation Plan: Same-Table Delivery Reconciliation

## Overview
Make `stock_delivery_daily` the single source of truth for both delivery values and reconciliation state, keyed by `instrument_token + trading_date`.

## Implementation Steps
- [x] Make `stock_id` nullable, add `reconciliation_status`, and change the table key to `instrument_token + trading_date`.
- [x] Extend delivery models and DAOs for nullable `stock_id`, reconciliation status, and token-keyed reads/upserts.
- [x] Add a delivery reconciliation service that compares expected universe rows against stored rows and fetches a date only when incomplete.
- [x] Refactor Remora to reconcile by date before scanning and to treat `MISSING_FROM_SOURCE` as known missing data.
- [x] Run focused tests and compile checks.
- [x] Add final review notes.

## Review
- `stock_delivery_daily` is now keyed by `instrument_token + trading_date`, with nullable `stock_id` and a persisted `reconciliation_status`.
- Added `DeliveryReconciliationService` plus reconciliation analyzer helpers so delivery fetches happen once per missing/incomplete date, not per stock.
- Remora now reconciles the latest available delivery date before scanning and ignores `MISSING_FROM_SOURCE` rows in delivery-ratio calculations.
- Verification:
  - `mvn -q -pl core -Dtest=DeliveryConfigServiceTest,DeliveryUniverseServiceTest,StockDeliveryMapperTest,DeliveryReconciliationAnalyzerTest,NseDeliverySourceAdapterTest,DeliverySourceValidationAnalyzerTest test`
  - `mvn -q -pl core,service,cron-job -DskipTests compile`
- Residual note: this slice assumes every configured NSE symbol resolves to a Kite `instrument_token`; if Kite instrument lookup drifts, reconciliation currently fails loudly instead of persisting a third unresolved-token status.

---

# Implementation Plan: Delivery Reconciliation Cron + Telegram

## Overview
Ship a standalone cron job that performs the same delivery reconciliation flow used by Remora, writes inspection artifacts, and sends Telegram lifecycle alerts so delivery import no longer depends on Remora-triggered refreshes.

## Implementation Steps
- [x] Add a standalone `DeliveryReconciliationJob` entrypoint in `cron-job`.
- [x] Add a compact reconciliation run-report model plus markdown and Telegram summary formatting.
- [x] Extend `DeliveryReconciliationService` with a read helper for configured-universe rows on a date.
- [x] Persist `latest.md` and `latest.json` under `build/reports/delivery-reconciliation/`.
- [x] Send Telegram start/completion/failure alerts for the job.
- [x] Run a live dry run against the real database/source and record the result.
- [x] Re-run the same date to confirm `alreadyComplete=true` behavior.

## Review
- Added `DeliveryReconciliationJob` as the standalone cron entrypoint for live dry runs and daily delivery reconciliation.
- Added a report factory/formatter so the job now emits both a human-readable markdown artifact and a JSON artifact from the same reconciliation result.
- Reused the existing Telegram lifecycle pattern with a concise completion summary showing resolved date, counts, watchlist-linked rows, non-watchlist rows, and source-fetch status.
- Kept Remora’s reconciliation safety net in place; the cron job is now the preferred daily import path, but Remora still protects scans if delivery has not been refreshed yet.
- Live dry run result for `2026-04-10`:
  - expected symbols: `502`
  - present rows: `501`
  - missing-from-source rows: `0`
  - nullable `stock_id` rows: `482`
  - first run fetched from NSE and wrote artifacts successfully
  - second run on the same date returned `alreadyComplete=true` and `fetchedFromSource=false`
- Current blocking data-quality issue:
  - `SCHNEIDER` is in the configured universe but does not resolve to a Kite `instrument_token`, so the job exits non-zero with a report instead of silently pretending the date is fully complete.

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
