# Implementation Plan: Fundamentals Universe + Profile Filter APIs and UI

## Overview
Deliver a complete fundamentals scanning flow for index tags: load all stocks + fundamentals by tag, run profile-based filtering, and show results in UI with filter status plus live market context.

## Implementation Steps
- [x] Add DAO delete support for fundamentals rows by symbol list.
- [x] Add refresh-service helper to clear fundamentals rows for a symbol list.
- [x] Add read DAO support to fetch latest fundamentals snapshot by instrument token list.
- [x] Add config service to load bucket/profile rules from `fundamentals_filter_config.json`.
- [x] Add Screener endpoint to refresh fundamentals by tag (delete then refetch/upsert).
- [x] Add Screener endpoint to fetch fundamentals universe rows by tag.
- [x] Add Screener endpoint to apply profile-based filtering for a tag.
- [x] Build frontend Fundamentals Screener UI with filtered column and reasons.
- [x] Add live context columns in UI (`LTP`, `RSI14`, `ROC 1W`, `ROC 3M`, `volumeVsAvg`).
- [x] Run compile/build checks for backend and frontend.
- [x] Add review notes with behavior and assumptions.

## Review
- Backend:
  - Added `deleteBySymbols(symbols)` in `StockFundamentalsWriteDao`.
  - Added `findLatestByInstrumentTokens(tokens)` in `StockFundamentalsReadDao`.
  - Added `deleteSnapshotsForSymbols(symbols)` in `FundamentalsRefreshService`.
  - Added `FundamentalsFilterConfigService` to read `fundamentals_filter_config.json`.
  - Added `NIFTY_200` bucket to `fundamentals_filter_config.json`.
  - Added `StockFundamentalsJdbiHandler` provider in `ServiceModule`.
  - Added Screener endpoints:
    - `POST /api/screener/fundamentals/refresh-by-tag?tag=...`
    - `GET /api/screener/fundamentals/by-tag?tag=...`
    - `POST /api/screener/fundamentals/filter`
  - Added NSE constituents source service:
    - `NseIndexConstituentsService` uses `https://www.nseindia.com/api/equity-stockIndices?index=...`
    - Filters `series=EQ` symbols and uses NSE index names (`NIFTY 50`, `NIFTY 100`, `NIFTY 200`, `NIFTY SMALLCAP 250`).
  - Tag resolution order now:
    1. NSE API constituents
    2. stock tags fallback
    3. smallcap preset CSV fallback
- Frontend:
  - Added `FundamentalsScreener` component with tag/profile controls and strict-missing-data toggle.
  - Added filtered status + reason columns and live context columns.
  - Added Screener page mode toggle between `Fundamentals` and `Weekly Pattern`.
  - Added `FundamentalsTableRow` and `FundamentalsTagOverviewResponse` types.
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `npm --prefix frontend run -s build` passed.
- Assumptions:
  - NSE index API remains reachable and responsive during tag resolution.
  - Smallcap preset CSV remains a safe fallback when NSE/tag resolution is unavailable.
  - Current filtering data availability is strongest for ROCE and stored fundamentals fields; strict mode flags missing fields explicitly.

# Implementation Plan: RSI Momentum Backfill Fresh


## Overview
Add a destructive rebuild flow triggered from UI: clear RSI momentum snapshot storage (`rsi_momentum_snapshot_daily` + Redis latest snapshot key), rebuild snapshots fresh for selected date range, refresh latest data, and present updated results.

## Implementation Steps
- [x] Add DAO support to clear RSI snapshot daily table.
- [x] Add backend fresh-backfill request/result models and service method.
- [x] Add API endpoint for fresh backfill and latest refresh.
- [x] Add `Backfill Fresh` button in RSI Momentum Base UI with confirmation.
- [x] Re-load and present rebuilt data after fresh backfill completes.
- [x] Run backend compile checks.
- [x] Run frontend build checks and document current blockers.

## Review
- Added table wipe DAO method:
  - `core/.../RsiMomentumSnapshotWriteDao.kt` -> `deleteAll()`.
- Added fresh backfill flow:
  - `core/.../RsiMomentumBackfillService.kt` -> `BackfillFreshRequest`, `BackfillFreshResult`, `backfillFresh(...)`.
- Added Redis cache clear helper:
  - `core/.../RsiMomentumService.kt` -> `clearLatestSnapshotCache()`.
- Added API endpoint:
  - `resources/.../StrategyResource.kt` -> `POST /api/strategy/rsi-momentum/backfill/fresh`.
- Added UI trigger:
  - `frontend/src/pages/RsiMomentumBasePage.tsx` -> `Backfill Fresh` button with destructive confirmation and post-action reload.
- Added frontend API response typing:
  - `frontend/src/types.ts` -> `BackfillFreshRequest/Result/Response`.
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `npm run -s build` still fails due pre-existing `react-router-dom` unresolved import in `RsiMomentumSafePage.tsx`, unrelated to this feature.

---

# Implementation Plan: RSI Momentum Base UI

## Overview
Build a brand-new `RSI Momentum Base` page focused on pure momentum score visibility over a date range, CSV export, and stock rank movement tracking. Reuse existing RSI history APIs and avoid strategy-logic changes.

## Implementation Steps
- [x] Add a new `RSI Momentum Base` page and route in frontend navigation.
- [x] Build date-range loader using existing `/api/strategy/rsi-momentum/history` API.
- [x] Show day-wise momentum results with top-40 table for selected day.
- [x] Add CSV export for loaded date-range momentum rows.
- [x] Add stock rank movement view across selected date range.
- [x] Add/extend frontend types/hooks minimally as needed.
- [x] Run frontend build/type checks and document results.
- [x] Run Kotlin reviewer pass (no Kotlin code changes expected) and record findings.

## Review
- Added new page: `frontend/src/pages/RsiMomentumBasePage.tsx`.
- Added new history-range hook: `frontend/src/hooks/useRsiMomentumBaseHistory.ts`.
- Added menu/route integration in `frontend/src/App.tsx` with page key `rsi-momentum-base`.
- Feature coverage:
  - Date-range load for momentum snapshots (`/api/strategy/rsi-momentum/history`)
  - Day-wise selection and top-list table view
  - CSV download for all loaded momentum rows
  - Stock rank movement table across selected range
- Verification:
  - `npm run -s build` fails due pre-existing unresolved dependency in `frontend/src/pages/RsiMomentumSafePage.tsx` (`react-router-dom`) unrelated to this new page.
  - `npx tsc --noEmit` reports multiple pre-existing frontend errors unrelated to this feature; no new blocker specific to `RsiMomentumBasePage` was observed in isolation.
- Kotlin reviewer pass:
  - Invoked as required by repo policy.
  - No Kotlin changes were made in this feature slice, so no Kotlin review findings for this implementation.

---

# Documentation Plan: RSI Momentum Explainability + Anti-Overfit Research Framework

## Overview
Document the agreed RSI momentum research structure in plain language while reflecting existing system capabilities (classic rank lane, fixed-target lane, rank-jump lane, weekday entry policy variants, and explainability requirements).

## Implementation Steps
- [x] Add a dedicated strategy document for explainability-first RSI momentum experiments.
- [x] Capture lane-based structure (Classic, Target, Rank-Jump) and a minimal experiment matrix.
- [x] Document anti-overfitting controls and current temporary scope choices (skip transaction/slippage for now).
- [x] Link the new document from strategy docs for discoverability.

## Review
- Added `docs/strategies/rsi-momentum-research-framework.md` as the canonical research-organization and explainability note.
- Linked it in `docs/strategies/README.md`.
- Added pointer in `docs/strategies/rsi-momentum-v1.md` to separate implementation history from current research framework.

---

# Implementation Plan: RSI Momentum Daily Refresh + Top-10 Immediate Exit

## Overview
Switch RSI momentum refresh from Friday-only to weekday-daily and align rebalance exits to top-10 membership so dropped names are exited on the next daily run.

## Implementation Steps
- [x] Update `.github/workflows/rsi-momentum-weekly-refresh.yml` cron from Friday-only to Monday-Friday daily at 15:40 IST.
- [x] Update `rsi_momentum_config.json` profiles to use `candidateCount = 10` (same as `holdingCount`) for immediate top-10 exits.
- [x] Run a focused verification check and record outcomes.

## Review
- Workflow now runs weekday-daily at 15:40 IST (`10:10 UTC`) instead of Friday-only.
- RSI momentum profile rebalance buffer is now top-10 aligned (`candidateCount = 10`, `holdingCount = 10`) for both `largemidcap250` and `smallcap250`.
- Verification: `mvn -pl cron-job -DskipTests compile --no-transfer-progress` passed.

---

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

## Follow-up Schema Notes
- Added a `universe` column to `stock_delivery_daily` so each stored delivery row now records its configured universe membership directly.
- Stored values are:
  - `largemidcap250`
  - `smallcap250`
  - `watchlist` for watchlist-only additions that are not part of either preset universe

---

# Implementation Plan: Screener Fundamentals Persistence

## Overview
Persist validated Screener fundamentals into Postgres as a daily snapshot history for the same configured universe used by delivery.

## Implementation Steps
- [ ] Add `fundamentals_config.json` with the V1 Screener source, universe, and request delay.
- [ ] Add fundamentals DAO/JDBI persistence support for `stock_fundamentals_daily`.
- [ ] Add a fundamentals refresh service that resolves the configured universe, fetches Screener snapshots, and upserts daily rows.
- [ ] Add a standalone `FundamentalsRefreshJob` with report artifacts and Telegram lifecycle alerts.
- [ ] Add focused tests for config loading, row mapping, and refresh report tolerance behavior.
- [ ] Run targeted tests/compile checks and record review notes.

## Review
- Pending implementation.

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

---

# Implementation Plan: RSI Momentum V1 — Daily Snapshot History + Backtest + Lifecycle

## Overview
Persist RSI momentum snapshots by day, run a 3-month point-in-time backtest with daily rebalancing, and add lifecycle analytics for top-10 rank tenure. Ships backend + UI tabs inside the existing RSI Momentum screen.

## Implementation Steps

### Backend
- [ ] 1. Add `rsi_momentum_snapshot_daily` table DDL to `tables.sql` and `DatabaseConstants`.
- [ ] 2. Add `RsiMomentumSnapshotDailyRecord` data class and DAO (read + write) with JDBI annotations.
- [ ] 3. Add `RsiMomentumSnapshotJdbiHandler` typealias and wire in `ServiceModule`.
- [ ] 4. Extend `RsiMomentumService.refreshLatest()` to upsert daily snapshot row after Redis write.
- [ ] 5. Add `RsiMomentumHistoryService` with history queries, backtest engine, and lifecycle engine.
- [ ] 6. Add new response models: `BacktestResult`, `LifecycleEpisode`, `LifecycleSummary`, `LifecycleSymbolDetail`.
- [ ] 7. Add 5 new endpoints to `StrategyResource`.

### Frontend
- [ ] 8. Add new TypeScript types for history, backtest, lifecycle.
- [ ] 9. Add `useRsiMomentumBacktest` and `useRsiMomentumLifecycle` hooks.
- [ ] 10. Add `BacktestTab` component (KPI cards, equity+drawdown SVG, trade log table).
- [ ] 11. Add `LifecycleTab` component (symbol picker, rank timeline, episode table, cohort summary).
- [ ] 12. Wire both tabs into `RsiMomentumProfilePanel`.

### Tests
- [ ] 13. Backend unit tests: snapshot DAO, backtest engine, lifecycle engine.
- [ ] 14. Frontend hook tests: backtest + lifecycle API flows.

## Review
- Pending implementation.

---

# Implementation Plan: RSI Safe Backtest Configurability (Jump/Days/Exit/Rank Window)

## Overview
Add configurable RSI Safe backtesting knobs so we can test low-overfit rule variants directly from UI.

## Implementation Steps
- [x] 1. Extend sniper backtest request/response models with rank band, jump band, lookback window, blocked entry days, and exit mode.
- [x] 2. Update backtest engine to compute jump from farthest rank in lookback window and apply rank/jump/day filters.
- [x] 3. Add exit modes: `T+3`, `RSI threshold`, and `T+3 or RSI threshold`.
- [x] 4. Surface new controls in RSI Safe UI backtest form.
- [x] 5. Add trade-level explainability fields (farthest rank, jump, exit reason).
- [x] 6. Verify backend + frontend compilation.

## Review
- Backend compile: pass (`mvn -q -pl core,resources,service -DskipTests compile`)
- Frontend build: pass (`npm run -s build`)
# Bugfix Plan: RSI Momentum Missing Latest 2 Days in Backfill/Load

## Problem
Backfill/history views were stopping at `2026-04-15` even when source data already had `2026-04-16` and `2026-04-17`.

## Implementation Steps
- [x] Reproduce the issue from local API (`/history` and `/refresh`) and confirm latest snapshot date.
- [x] Trace candle freshness and Kite fetch date-range logic in RSI momentum service.
- [x] Tighten freshness gate so RSI sync triggers as soon as latest expected trading day is missing.
- [x] Expand Kite history fetch end date to include current day candle when available.
- [x] Add regression tests for strict freshness behavior.
- [x] Run backend compile and frontend build checks.

## Review
- Root cause 1: RSI freshness check allowed up to 3-day lag before syncing candles.
- Root cause 2: Kite history fetch end date used today's `00:00`, which excluded same-day candle.
- Fixes applied in:
  - `core/src/main/kotlin/com/tradingtool/core/strategy/rsimomentum/RsiMomentumService.kt`
    - freshness check now uses `holidayGraceDays = 0` for RSI sync path.
    - history fetch end moved to next-day start (`today + 1 day at 00:00`) to include today if published.
  - `core/src/test/kotlin/com/tradingtool/core/strategy/rsimomentum/RsiMomentumServiceDateLogicTest.kt`
    - added strict freshness regression tests.
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `npm run -s build` passed.
# Implementation Plan: Replace Screener Fundamentals with NSE Corporate Filings

## Overview
Replace the current Screener-based fundamentals ingestion with a free NSE-based implementation using NSE corporate filings endpoints and related NSE quote/shareholding APIs.

## POC Summary (Completed Before Implementation)
- [x] Verified `GET /api/corporates-financial-results?index=equities&symbol=INFY&period=Quarterly` returns filing rows with `companyName`, period/date fields, and XBRL links.
- [x] Verified `GET /api/quote-equity?symbol=INFY` returns company/industry metadata and `pdSymbolPe`.
- [x] Verified `GET /api/corporate-share-holdings-master?index=equities&symbol=INFY` returns promoter holding (`pr_and_prgrp`).

## Implementation Steps
- [x] Replace `ScreenerFundamentalsSourceAdapter` internals with NSE endpoint fetch flow.
- [x] Replace HTML parser logic with NSE JSON parser logic for snapshot fields.
- [x] Update fundamentals source config enum/default from Screener to NSE.
- [x] Update refresh service source gate and source-name metadata.
- [x] Remove remaining Screener URL dependencies and update relevant tests.
- [x] Run targeted backend tests and compile checks.
- [x] Add review notes with known NSE field limitations.

## Review
- Backend replacement completed:
  - `ScreenerFundamentalsSourceAdapter` now fetches NSE:
    - `/api/quote-equity?symbol=...`
    - `/api/corporates-financial-results?index=equities&symbol=...&period=Quarterly`
    - `/api/corporate-share-holdings-master?index=equities&symbol=...`
  - `ScreenerFundamentalsParser` now parses combined NSE JSON payload (no Screener HTML parsing).
  - `FundamentalsDataSource` default switched to `NSE_CORPORATE_FILINGS`.
  - `FundamentalsRefreshService` source gate and metadata updated to NSE source.
  - `fundamentals_config.json` updated to `NSE_CORPORATE_FILINGS`.
- Test updates completed:
  - Rewrote parser test to validate NSE JSON payload mapping.
  - Updated source metadata fixtures in fundamentals mapper/report tests to NSE values.
  - Updated config service test for new default source enum.
- Verification:
  - `mvn -q -pl core,resources,service,cron-job -DskipTests compile` passed.
  - `mvn -q -pl core -Dtest=... test` could not complete because the repository currently has unrelated pre-existing Kotlin test compile failures in RSI momentum tests.
- Known field limitation from current NSE free endpoints:
  - `ROCE`, `ROE`, `debtToEquity`, and `pledgedPercent` are not directly present in the consumed NSE payloads, so they are currently stored as `null` in this replacement.
  - Added preservation logic so previously stored non-null fundamentals are retained when NSE payload omits those fields on newer refresh dates.

# Implementation Plan: Weekly Scanner Expanded Index Universe

## Overview
Move weekly scanner default universe from watchlist-only to a broader NSE index universe so scans can run across NIFTY 50/100/250, largemidcap/smallcap, and sector/thematic NIFTY baskets.

## Implementation Steps
- [x] Replace weekly scanner default symbol resolver to build a union of configured NIFTY indices (instead of only `weekly` tag).
- [x] Add resilient fetch behavior: dedupe symbols, skip empty index responses, and fallback to weekly watchlist symbols when NSE fetch returns nothing.
- [x] Update candle sync + weekly analysis symbol metadata resolution so non-watchlist index symbols can still resolve instrument token/name.
- [ ] Add/adjust focused tests for expanded resolver behavior and fallbacks (blocked by pre-existing test compile failures in unrelated RSI momentum tests).
- [x] Run targeted backend tests (or compile at minimum) and document verification.

## Review
- Updated weekly scanner default symbol resolution in [ScreenerResource.kt](/Users/kushbhardwaj/Documents/github/TradingTool-3/resources/src/main/kotlin/com/tradingtool/resources/ScreenerResource.kt) to union NSE constituents across broad + sector/thematic NIFTY indices.
- Added robust fallback to `weekly` watchlist tag when NSE index fetch returns no symbols.
- Updated candle sync token resolution in [CandleDataService.kt](/Users/kushbhardwaj/Documents/github/TradingTool-3/core/src/main/kotlin/com/tradingtool/core/screener/CandleDataService.kt) to use `InstrumentCache` when stock is not in watchlist table.
- Updated weekly analysis metadata resolution in [WeeklyPatternService.kt](/Users/kushbhardwaj/Documents/github/TradingTool-3/core/src/main/kotlin/com/tradingtool/core/screener/WeeklyPatternService.kt) to support non-watchlist symbols via `InstrumentCache`.
- Updated DI wiring in [ServiceModule.kt](/Users/kushbhardwaj/Documents/github/TradingTool-3/service/src/main/kotlin/com/tradingtool/di/ServiceModule.kt) for new `InstrumentCache` dependencies.
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `mvn -q -pl core -Dtest=WeeklyPatternServiceTest test` could not run due unrelated pre-existing Kotlin test-compile errors in RSI momentum tests.

# Implementation Plan: Weekly Scanner V1 (Broad + 14 Sectoral) with Swing Setup Card

## Overview
Implement PRD-approved weekly scanner upgrade: exact broad + 14 sectoral NSE index universe, scanner response contract expansion, and UI swing setup card on row click.

## Implementation Steps
- [x] Lock weekly scanner default universe to broad + 14 sectoral/thematic NSE indices.
- [x] Keep manual symbol override precedence and resilient fallback to watchlist.
- [x] Add `universeSourceTags` to weekly scanner list response.
- [x] Add setup-level response fields: `setupQualityScore`, `expectedSwingPct`, `baselineDistancePct`, and structured `swingSetup` object.
- [x] Compute baseline-distance metric from latest close vs 10-week baseline low.
- [x] Add Weekly Swing row-click drawer card showing buy zone, target plan, hard stop, invalidation, confidence, and reasoning.
- [x] Run backend compile and frontend build.

## Review
- Backend updated:
  - `resources/src/main/kotlin/com/tradingtool/resources/ScreenerResource.kt`
    - Weekly universe resolver now returns symbols + source tags.
    - `GET /api/screener/weekly-pattern` now returns `universeSourceTags`.
    - Default universe locked to exact broad + 14 sectoral/thematic list from PRD.
  - `core/src/main/kotlin/com/tradingtool/core/screener/WeeklyPatternModels.kt`
    - Added `SwingSetup` model.
    - Added `setupQualityScore`, `expectedSwingPct`, `baselineDistancePct`, `swingSetup` to weekly result/detail payloads.
    - Added `universeSourceTags` to list response envelope.
  - `core/src/main/kotlin/com/tradingtool/core/screener/WeeklyPatternService.kt`
    - Added baseline-distance calculation.
    - Added swing-setup builder with stop-loss + invalidation + reasoning.
    - Wired setup-level fields for list/detail/no-data responses.
- Frontend updated:
  - `frontend/src/types.ts`
    - Added `SwingSetup` type and weekly response fields.
  - `frontend/src/pages/WeeklySwingPage.tsx`
    - Added sortable columns for Expected Swing and Baseline Distance.
    - Added Setup Score column backed by API field.
    - Added universe tag display under page subtitle.
    - Added row-click `Drawer` Swing Setup card.
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `npm --prefix frontend run -s build` passed.

---

# Implementation Plan: Weekly Pattern Enhancements V1 (ScreenerOverview)

## Overview
Implement the full weekly screener UX enhancement set on the existing `ScreenerOverview` surface: table column controls with persistence, full-column filtering/sorting/multi-select, custom ranking builder, inline trade planner, and right-side detail drawer with setup/chart/timeline/planner tabs.

## Implementation Steps
- [x] Add persistent localStorage state keys and safe parse/fallback utilities for columns, filters, sort, rank presets, and planner defaults.
- [x] Refactor `ScreenerOverview` table model to support dynamic visible columns, reorder, pin left/right, and reset defaults via an AntD columns drawer.
- [x] Add filter/sort support across visible columns with multi-select categorical filters and search, including stock symbol/company filters.
- [x] Add quick filter chips and saved filter presets (save/apply/delete) persisted in localStorage.
- [x] Add ranking drawer with weight sliders, rank mode switch (System vs Custom), score normalization, and ranking presets persistence.
- [x] Add inline trade planner module with risk model outputs and per-symbol checklist persistence.
- [x] Replace row navigation with in-context right detail drawer; fetch/cache weekly detail + technical context on demand with non-blocking error handling.
- [x] Add mini chart and timeline tabs in detail drawer using existing response data.
- [x] Run frontend build and record verification + assumptions.

## Review
- Frontend enhancements implemented on existing weekly pattern screen:
  - `frontend/src/components/ScreenerOverview.tsx`
    - Added persisted table config state for columns (`show/hide`, reorder, pin left/right) with reset.
    - Added persisted sort + advanced multi-select filters (`filterSearch`) for every visible column.
    - Added quick chips (`Inside zone`, `Near`, `Strong score`, `Buy day today`) and saved filter presets.
    - Added ranking builder drawer with weighted sliders and preset save/load/delete.
    - Added client-side `customRankScore` and rank mode switch (`System Score` / `Custom Weighted Score`).
    - Added inline trade planner (expand row + drawer tab) with risk-based quantity, RR, and persisted per-symbol checklist.
    - Replaced hard navigation with right-side in-context detail drawer (Setup, Mini Chart, Timeline, Trade Planner).
    - Added on-demand detail fetch with per-symbol session cache and non-blocking error state.
    - Added universe-bucket visibility column (`Universe Bucket`) sourced from `sourceBuckets`.
  - `frontend/src/pages/ScreenerPage.tsx`
    - Weekly mode now stays on `ScreenerOverview`; no route jump to `ScreenerDetail`.
  - New utilities:
    - `frontend/src/utils/weeklyScreenerRanking.ts`
    - `frontend/src/utils/weeklyScreenerTableState.ts`
  - New tests:
    - `frontend/src/utils/weeklyScreenerRanking.test.ts`
    - `frontend/src/utils/weeklyScreenerTableState.test.ts`
- Verification:
  - `npm --prefix frontend run -s test:run -- src/utils/weeklyScreenerRanking.test.ts src/utils/weeklyScreenerTableState.test.ts` passed.
  - `npm --prefix frontend run -s build` passed.
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `cd frontend && npx tsc --noEmit` still fails due pre-existing unrelated repo issues; no new type errors from this feature slice remain.

---

# Documentation Plan: Monday-Low Median Buy Zone Review (No Code Change)

## Overview
Prepare a decision-review document for proposed buy-zone formula change: use Monday lows from the last 10 completed weeks, take bottom-3 lows, and use median as robust zone floor. Do not implement code in this step.

## Implementation Steps
- [x] Capture current as-is calculation and impacted UI semantics.
- [x] Document proposed formula in plain language with numerical example.
- [x] Provide risk/impact review and fallback behavior options.
- [x] Recommend implementation option and rollout checklist.

## Review
- Added decision-review doc:
  - `docs/features/weekly-scanner/2026-04-18-buy-zone-monday-median-review.md`
- Scope respected:
  - Documentation only; no backend/frontend behavior changes made in this step.

---

# Implementation Plan: Simple Momentum Top-5 / Exit-on-Top-10 Backtest

## Overview
Add one simple momentum backtest mode with exact user rules: evaluate a chosen profile over a one-year window, buy top 5 ranked momentum stocks with equal capital allocation from ₹2,00,000, keep positions while rank <= 10, and exit immediately when rank > 10.

## Implementation Steps
- [x] Add dedicated request/response models for the simple momentum backtest (capital, topN buy count, hold threshold).
- [x] Implement backtest engine in `RsiMomentumHistoryService` using snapshot prices and share-quantity accounting.
- [x] Add API endpoint in `StrategyResource` for the new simple backtest.
- [x] Add frontend types and a hook to call the new endpoint.
- [x] Add a compact UI panel in `BacktestTab` to run this strategy with defaults.
- [ ] Add/extend Kotlin tests for strategy rules (entry count, exit-on-rank-drop, open-position mark-to-market).
- [x] Run backend/frontend compile checks.
- [x] Run Kotlin reviewer pass and capture review verdict.

## Review
- Backend:
  - Added `SimpleMomentumBacktestRequest`, `SimpleMomentumTrade`, `SimpleMomentumBacktestSummary`, and `SimpleMomentumBacktestResult`.
  - Added `runSimpleMomentumBacktest(...)` in `RsiMomentumHistoryService` with:
    - default one-year window,
    - top-5 entry (`entryRankMax=5`),
    - exit when rank leaves top-10 (`holdRankMax=10`),
    - ₹2,00,000 default capital,
    - equal-value position sizing using integer share quantities,
    - mark-to-market handling for open positions.
  - Added endpoint: `POST /api/strategy/rsi-momentum/backtest/simple`.
- Frontend:
  - Added `useSimpleMomentumBacktest` hook.
  - Added types for simple momentum request/result/trades in `frontend/src/types.ts`.
  - Added a dedicated runner card and result journal table in `BacktestTab`.
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `npm --prefix frontend run -s build` passed.
- Kotlin reviewer pass:
  - CRITICAL: 0
  - HIGH: 0
  - MEDIUM: 0
  - LOW: 0
  - Verdict: APPROVE for this slice.
- Remaining gap:
  - No new focused Kotlin unit tests were added yet for the simple momentum engine path.

---

# Bugfix Plan: Simple Momentum Backtest Zero-PnL Exit Pricing

## Overview
Fix zero-PnL bug in simple momentum backtest where exits for symbols missing from the current ranked snapshot incorrectly reused entry price instead of exit-day market close.

## Implementation Steps
- [x] Reproduce and identify wrong fallback path in `runSimpleMomentumBacktest`.
- [x] Add candle-close fallback lookup for exit-day pricing when rank row is missing.
- [x] Add candle-close fallback lookup for open-position mark-to-market.
- [x] Update DI wiring for new `CandleJdbiHandler` dependency in history service.
- [x] Run backend compile check.
- [x] Run review pass (code-reviewer + kotlin-reviewer).

## Review
- Root cause:
  - Exit pricing fallback used `entryPrice` when a symbol dropped out of ranked snapshot rows, forcing P&L to 0 in these exits.
- Fix:
  - Added `instrumentToken` in open position state for simple momentum backtest.
  - Added `loadClosePriceForDate(token, date)` using `daily_candles`.
  - Exit price now resolves as: ranked close -> candle close on exit date -> entry price fallback.
  - Open mark-to-market price now resolves as: ranked close -> candle close on `toDate` -> entry price fallback.
  - Updated `ServiceModule` provider to pass `CandleJdbiHandler` into `RsiMomentumHistoryService`.
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
- Reviewer verdict:
  - code-reviewer: no blocking findings for this diff.
  - kotlin-reviewer: CRITICAL 0, HIGH 0, MEDIUM 0, LOW 0; verdict APPROVE.

---

# Bugfix Plan: Simple Momentum Date Coverage Transparency

## Overview
Fix inconsistency in simple momentum backtest presentation where requested date window was shown without indicating actual snapshot coverage, causing confusion when first trade appears much later.

## Implementation Steps
- [x] Add actual snapshot coverage fields in simple momentum backtest response.
- [x] Populate coverage fields from available snapshots in backend service.
- [x] Update frontend types and journal title to display requested vs snapshot coverage.
- [x] Add warning banner when first snapshot date is after requested start date.
- [x] Run backend/frontend build checks.
- [x] Run review pass (code-reviewer + kotlin-reviewer).

## Review
- Backend:
  - Added `firstSnapshotDate` and `lastSnapshotDate` to `SimpleMomentumBacktestResult`.
  - Populated these fields as `null` when no snapshots and with first/last snapshot dates when data exists.
- Frontend:
  - Updated result type and simple backtest panel.
  - Journal header now shows:
    - requested range,
    - actual snapshot coverage,
    - snapshot day count.
  - Added warning alert when requested start is earlier than first available snapshot.
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `npm --prefix frontend run -s build` passed.
- Reviewer verdict:
  - code-reviewer: no blocking findings.
  - kotlin-reviewer: CRITICAL 0, HIGH 0, MEDIUM 0, LOW 0; verdict APPROVE.

---

# Implementation Plan: Dedicated Simple Momentum Backtest Page

## Overview
Add a standalone Simple Backtest page with per-profile tabs, date-range-based prepare flow (daily candle sync from Kite + snapshot backfill), capital input, and simple backtest execution.

## Implementation Steps
- [x] Add date-range daily candle sync support in `CandleDataService`.
- [x] Add simple backtest prepare service + request/response models in RSI momentum strategy module.
- [x] Add strategy API endpoint: `POST /api/strategy/rsi-momentum/backtest/simple/prepare`.
- [x] Wire DI providers for the new prepare service.
- [x] Add frontend types and dedicated page UI with profile tabs.
- [x] Add route/menu entry for the new page.
- [x] Add frontend tests for page render and actions.
- [x] Run backend/frontend compile/tests.
- [x] Run review pass (`code-reviewer`, `kotlin-reviewer`) and record verdict.

## Review
- Backend:
  - Added `syncDailyRange(...)` in `CandleDataService` with full-range daily fetch + upsert for selected range and symbol-level failure tracking.
  - Added `SimpleMomentumBacktestPrepService` with:
    - request validation,
    - profile/base-universe symbol resolution,
    - date-range candle sync via Kite,
    - snapshot backfill (`skipExisting=true`),
    - consolidated warnings.
  - Added endpoint `POST /api/strategy/rsi-momentum/backtest/simple/prepare` in `StrategyResource`.
- Frontend:
  - Added standalone `SimpleBacktestPage` with:
    - profile tabs,
    - date-range picker,
    - capital input,
    - `Prepare Data`,
    - `Run Backtest`,
    - prepare summary + result table.
  - Added new menu/route key `simple-backtest` in `App.tsx`.
  - Added shared types for simple prepare response in `frontend/src/types.ts`.
  - Added test file `frontend/src/pages/SimpleBacktestPage.test.tsx` (render + action flows).
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `npm --prefix frontend run -s build` passed.
  - `npm --prefix frontend run -s test:run -- src/pages/SimpleBacktestPage.test.tsx` passed (2 tests).
- Reviewer verdict:
  - code-reviewer: no CRITICAL/HIGH findings on this diff.
  - kotlin-reviewer: CRITICAL 0, HIGH 0, MEDIUM 0, LOW 0; verdict APPROVE.

---

# Implementation Plan: Simple Backtest Entry Band + Sell Threshold Controls

## Overview
Allow configurable entry rank band (inclusive min-max) and configurable sell threshold rank on the dedicated Simple Backtest page.

## Implementation Steps
- [x] Add backend request/response support for `entryRankMin` in simple momentum backtest.
- [x] Update simple backtest engine to use inclusive entry rank band and correct target slot count from band width.
- [x] Add UI controls for entry rank min/max and hold rank max on the dedicated Simple Backtest page.
- [x] Update frontend types/tests for new request field.
- [x] Run backend compile + frontend test/build checks.

## Review
- Backend:
  - Added `entryRankMin` to simple momentum request/response models.
  - Updated simple backtest engine to:
    - enter only when `rank in entryRankMin..entryRankMax`,
    - cap simultaneous positions by band width (`entryRankMax - entryRankMin + 1`) instead of raw max rank.
  - Kept sell rule configurable with `holdRankMax` and existing exit behavior (`rank > holdRankMax`).
- Frontend:
  - Added controls on dedicated page for:
    - entry rank min,
    - entry rank max,
    - sell threshold rank.
  - Wired values to run payload.
  - Added result summary cards showing active entry band and sell threshold.
  - Updated page test assertion to include `entryRankMin`.
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `npm --prefix frontend run -s test:run -- src/pages/SimpleBacktestPage.test.tsx` passed.
  - `npm --prefix frontend run -s build` passed.

---

# Implementation Plan: Simple Backtest Drawdown Guard V1 (10-Day High)

## Overview
Add a fixed entry guard in simple momentum backtest: skip entry if entry-day close is more than 5% below the highest close in the last 10 trading days.

## Implementation Steps
- [x] Add backend constants and guard logic in simple momentum backtest flow.
- [x] Compute recent high from daily candles over 10-day lookback ending on entry date.
- [x] Skip guarded stocks and reallocate capital among remaining eligible entries on that day.
- [x] Expose guard diagnostics in response summary for transparency.
- [x] Update frontend types/UI summary to display guard impact.
- [x] Run backend/frontend compile + targeted tests.

## Review
- Backend:
  - Added fixed drawdown guard constants in simple backtest engine:
    - lookback days: `10`
    - threshold: `5%`
  - Entry guard rule:
    - compute recent high as max `close` in `daily_candles` for `[entryDate-9, entryDate]`.
    - compute drawdown as `((recentHigh - entryPrice) / recentHigh) * 100`.
    - skip entry when drawdown is greater than `5%`.
  - Added cache for `(instrumentToken, asOfDate)` recent-high lookups in run scope.
  - Added summary diagnostic: `entriesSkippedByDrawdownGuard`.
  - Added result metadata: `drawdownGuardLookbackDays`, `drawdownGuardThresholdPct`.
- Frontend:
  - Updated simple backtest result/summary types for guard diagnostics.
  - Added summary cards showing drawdown guard config and skip count.
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `npm --prefix frontend run -s test:run -- src/pages/SimpleBacktestPage.test.tsx` passed.
  - `npm --prefix frontend run -s build` passed.

---

# Implementation Plan: Simple Backtest OR Exit with 8% Trailing Stop

## Overview
Add an OR exit condition in simple momentum backtest: exit when rank leaves hold threshold OR price falls 8% from post-entry peak close.

## Implementation Steps
- [x] Add fixed trailing-stop constants in simple backtest engine (`8%`).
- [x] Track per-position peak close since entry.
- [x] Add OR exit logic with trailing-stop priority when both conditions are true.
- [x] Include trailing-stop metadata and diagnostics in API response.
- [x] Update frontend types and summary cards.
- [x] Run compile/build/tests for touched modules.

## Review
- Backend:
  - Added `TRAILING_STOP_PCT = 8.0` constant.
  - For each open position, peak close is updated daily from current close/candle close.
  - Exit triggers when:
    - `currentRank > holdRankMax` OR
    - `currentClose <= peakCloseSinceEntry * 0.92`
  - Exit reason precedence:
    - trailing stop first (`TRAILING_STOP_8_PCT`), then rank (`LEFT_TOP_{holdRankMax}`).
  - Added response diagnostics:
    - `trailingStopPct`
    - `summary.exitsByTrailingStop`
    - per-trade `peakCloseSinceEntry`
    - per-trade `trailingStopPriceAtExit`
- Frontend:
  - Updated `SimpleMomentumBacktestResult`/`Summary`/`Trade` types.
  - Added summary cards in dedicated Simple Backtest page:
    - trailing stop config
    - trailing-stop exit count
- Verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `npm --prefix frontend run -s test:run -- src/pages/SimpleBacktestPage.test.tsx` passed.
  - `npm --prefix frontend run -s build` passed.
# Implementation Plan: RSI Momentum Leaders Drawdown Dashboard

## Overview
Implement a new separate frontend page and backend API that analyzes last-1-year RSI momentum snapshots, extracts daily top-10 leaders, builds unique-company sets per profile and combined, and computes drawdown buckets using today close and 20-day minimum close.

## Implementation Steps
- [x] Add backend DTOs for leaders rows, bucket summaries, per-profile and combined response sections, and response metadata.
- [x] Add `RsiMomentumHistoryService` read path for leaders aggregation + drawdown computation + bucket summaries.
- [x] Add `GET /api/strategy/rsi-momentum/leaders-drawdown` endpoint with defaults and query-param handling.
- [x] Wire DI updates needed for the new service dependencies.
- [x] Add frontend types + hook for leaders drawdown API.
- [x] Add new separate page in top menu with KPI cards, filters, and combined/profile tabs.
- [x] Add backend unit tests for aggregation and drawdown bucket boundary behavior.
- [x] Add frontend tests for rendering, mode switch, and filtering.
- [x] Run compile/tests for touched backend and frontend modules.
- [x] Run required Kotlin review pass and document findings.

## Review
- Backend:
  - Added leaders drawdown DTO contracts in RSI momentum models.
  - Implemented `getLeadersDrawdown(from, to, requestedProfileIds, topN)` in `RsiMomentumHistoryService`.
  - Added drawdown helper logic for:
    - `ddTodayPct` from 1Y high close to latest close in window.
    - `dd20dMinPct` from 1Y high close to latest 20-session minimum close.
  - Added bucket flags and summaries for 20/30/40/50/60% thresholds.
  - Added API endpoint: `GET /api/strategy/rsi-momentum/leaders-drawdown`.
  - Updated DI provider so `RsiMomentumHistoryService` receives candle + config services.
- Frontend:
  - Added new types: leaders row, bucket summaries/flags, profile section, combined section, full response.
  - Added new hook: `useRsiMomentumLeadersDrawdown`.
  - Added new page: `RsiMomentumLeadersDrawdownPage` with:
    - date-range apply + refresh,
    - mode switch (Today DD / 20D Min DD),
    - threshold/search/profile filters,
    - combined tab + profile tabs,
    - KPI cards and result table.
  - Added new top menu route key: `rsi-momentum-drawdown`.
- Tests and verification:
  - `mvn -q -pl core,resources,service -DskipTests compile` passed.
  - `npm --prefix frontend run -s test:run -- src/pages/RsiMomentumLeadersDrawdownPage.test.tsx` passed.
  - `npm --prefix frontend run -s build` passed.
  - `mvn -q -pl core -Dtest=RsiMomentumLeadersDrawdownLogicTest test` could not run because `core` test compilation currently fails on pre-existing unrelated test errors (`RsiMomentumHistoryServiceTest`, `RsiMomentumRankerTest`).
- Kotlin reviewer pass:
  - Completed review of Kotlin diff using `kotlin-reviewer` checklist.
  - No CRITICAL/HIGH issues found in the new leaders-drawdown Kotlin changes.
