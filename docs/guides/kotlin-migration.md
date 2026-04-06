# Kotlin Migration Plan (TradingTool-3)

Status: Approved for execution  
Date: 2026-02-19  
Scope owner: Backend/Trading runtime

## 1) Goal

Migrate the backend and trading runtime from Python to Kotlin/JVM while preserving:

- Existing API behavior used by the current frontend and automations.
- Existing Supabase schema and data.
- Existing deployment model (Render + Supabase + GitHub Pages).

Source reference for parity:

- `/Users/kushbhardwaj/Documents/github/TradingTool-2`

## 2) Scope

### In scope

- FastAPI services -> Ktor services.
- Watchlist CRUD APIs and service validation.
- Telegram webhook and file download flow.
- Supabase integration for current tables.
- Trading indicator/backtesting stack migration (`vectorbt`, `pandas-ta`) -> ta4j.
- CLI operations used for service checks.
- CI checks equivalent to current `ruff` + `mypy`.

### Out of scope (for this migration)

- Frontend rewrite (React + Ant Design stays as is).
- Database schema redesign.
- New trading features not present in current Python behavior.
- Multi-broker expansion beyond currently used APIs.

## 3) Current Python Inventory (Source Repo: TradingTool-2)

| Source path | Responsibility | Kotlin target module |
| --- | --- | --- |
| `src/presentation/api/telegram_webhook_app.py` | app bootstrap, CORS, health, Telegram webhook | `presentation/http/App.kt`, `presentation/http/TelegramWebhookRoutes.kt` |
| `src/presentation/api/watchlist_router.py` | watchlist REST endpoints | `presentation/http/WatchlistRoutes.kt` |
| `src/application/services/watchlist_service.py` | validation + business rules | `application/service/WatchlistService.kt` |
| `src/infrastructure/repositories/watchlist_dao.py` | Supabase table operations | `infrastructure/repository/SupabaseWatchlistRepository.kt` |
| `src/infrastructure/supabase/*` | client provider, env config, models | `infrastructure/supabase/*` |
| `src/infrastructure/telegram/*` | Telegram API client + module | `infrastructure/telegram/*` |
| `src/presentation/cli/supabase_cli.py` | health checks via CLI | `presentation/cli/SupabaseCli.kt` |
| `src/presentation/cli/telegram_*.py` | webhook + polling tooling | `presentation/cli/TelegramCli.kt` |

## 4) Target Kotlin Architecture

Use a Maven multi-module structure with clear responsibility split.

```text
tradingtool-3/
  pom.xml  (parent)
  core/
    src/main/kotlin/com/tradingtool/core/
    src/test/kotlin/com/tradingtool/core/
  service/
    src/main/kotlin/com/tradingtool/
    src/main/resources/serverConfig.yml
    src/test/kotlin/com/tradingtool/
  event-service/
    src/main/kotlin/com/tradingtool/event/
    src/test/kotlin/com/tradingtool/event/
  cron-job/
    src/main/kotlin/com/tradingtool/cron/
    src/test/kotlin/com/tradingtool/cron/
```

## 5) API Compatibility Contract (Must Keep)

### Service/health endpoints

- `GET /` -> `{ "service": "TradingTool-3", "status": "ok" }`
- `GET /health` -> `{ "status": "ok" }`
- `GET /health/supabase` -> same shape as current health model

### Telegram endpoint

- `POST /telegram/webhook`
- Preserve secret header validation:
  - Header: `X-Telegram-Bot-Api-Secret-Token`
  - 403 on mismatch when secret is configured

### Watchlist endpoints

Preserve current route surface under `/api/watchlist`:

- `GET /tables`
- stocks: create/list/get/update/delete
- watchlists: create/list/get/update/delete
- watchlist items: create/list/get/update/delete

### Environment variables

Must preserve current env names during migration:

- `SUPABASE_DB_URL`
- `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `TELEGRAM_WEBHOOK_SECRET`
- `TELEGRAM_DOWNLOAD_DIR`, `TELEGRAM_POLL_TIMEOUT_SECONDS`, `TELEGRAM_REQUEST_TIMEOUT_SECONDS`
- `TELEGRAM_ERROR_RETRY_SLEEP_SECONDS`, `TELEGRAM_MAX_RETRY_SLEEP_SECONDS`
- `CORS_ALLOWED_ORIGINS`
- `RENDER_EXTERNAL_URL`
- `GITHUB_PAGES_URL`

## 6) Migration Strategy

Use a phased strangler strategy: run Kotlin in parallel, then switch traffic after parity tests pass.

### Phase 0: Baseline freeze

- Create API fixtures for existing Python responses.
- Record request/response examples for all watchlist and webhook paths.
- Add an endpoint parity checklist.

Exit gate:

- Baseline fixtures committed and reviewed.

### Phase 1: Kotlin skeleton

- Create Kotlin JVM multi-module project (`core`, `service`, `event-service`, `cron-job`) with Maven and JDK 21.
- Add Ktor app, JSON serialization, logging, config loading.
- Implement `GET /` and `GET /health`.

Exit gate:

- Kotlin service boots in local + CI and passes smoke checks.

### Phase 2: Domain + validation parity

- Port DTOs/models from Python to Kotlin data classes.
- Port all watchlist validation rules from `watchlist_service.py`.

Exit gate:

- Validation behavior matches Python fixtures.

### Phase 3: Supabase watchlist repository

- Implement repository against Supabase via Kotlin client stack.
- Port watchlist CRUD behavior exactly.

Exit gate:

- Contract tests for watchlist routes pass against staging Supabase.

### Phase 4: Telegram webhook flow

- Port webhook parsing and supported message handling (text/photo/document).
- Preserve file naming and storage behavior where practical.

Exit gate:

- Webhook integration tests pass with real Telegram test bot.

### Phase 5: Trading stack migration

- Replace `vectorbt` and `pandas-ta` usage with ta4j indicators + backtest engine.
- Keep strategy interfaces simple and explicit.

Exit gate:

- Existing strategy outputs match agreed tolerance bands.

### Phase 6: CLI + operations

- Port required CLI flows for health and webhook admin.
- Add structured logs and operational diagnostics.

Exit gate:

- All operational runbooks executable in Kotlin-only service.

### Phase 7: Cutover + decommission

- Deploy Kotlin service as parallel Render service.
- Run shadow tests for 3-7 days.
- Switch production traffic to Kotlin service.
- Keep Python service available for emergency rollback window.

Exit gate:

- Zero critical regression incidents during cutover window.

## 7) Testing and Quality Gates

### Test layers

- Unit tests: domain rules, validators, pure service logic.
- Integration tests: Supabase repo + Telegram API client.
- Contract tests: endpoint parity with Python fixtures.
- Smoke tests: health endpoints and key CRUD path.

### Static checks

- `detekt` for code quality.
- `ktlint` for formatting/lint.
- Strict Kotlin compiler warnings treated as errors where possible.

### Release gate

- No failing tests.
- No unresolved parity deviations.
- Performance within agreed thresholds.

## 8) Deployment Plan

1. Stand up `tradingtool-kotlin` on Render (separate service).
2. Mirror env vars and secrets.
3. Run health + integration probes.
4. Execute shadow traffic checks.
5. Switch production routing.
6. Monitor telemetry for 24h/72h checkpoints.

## 9) Rollback Plan

Rollback is immediate traffic revert to Python service if any critical issue appears:

- Data corruption risk.
- API contract break affecting frontend.
- Order/risk logic inconsistency.
- Persistent webhook processing failure.

Keep Python service warm until Kotlin is stable in production for at least 7 days.

## 10) Risks and Mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| `supabase-kt` is community-maintained | API/client drift risk | Keep a fallback path using direct PostgREST calls via Ktor client |
| No official Groww Kotlin SDK | More custom code | Build thin typed HTTP adapter + strict tests against sandbox/staging |
| Trading result mismatch vs Python | Strategy trust risk | Golden dataset comparison + tolerance thresholds |
| Kotlin dependency drift | Build/runtime instability | Centralize versions in a single dependency catalog and pin key libs |

## 11) Proposed Timeline

Assuming start date: February 20, 2026.

- Sprint 1 (2026-02-20 to 2026-02-29): Phases 0-1
- Sprint 2 (2026-03-01 to 2026-03-14): Phases 2-3
- Sprint 3 (2026-03-15 to 2026-03-28): Phases 4-5
- Sprint 4 (2026-03-29 to 2026-04-11): Phases 6-7 + cutover

## 12) Definition of Done

Migration is complete only when:

- Kotlin service fully handles production traffic.
- Python backend service is no longer required for runtime behavior.
- Endpoint/API parity checks are green.
- Trading indicator/backtest outputs are validated.
- Runbooks and docs are updated for Kotlin operations.

## 13) First PR Checklist

Use this as the immediate execution checklist for the first migration PR:

1. Create migration branch (`codex/kotlin-migration-bootstrap`).
2. Scaffold Kotlin/JVM multi-module project (`core`, `service`, `event-service`, `cron-job`) with Maven and JDK 21.
3. Add Ktor + serialization + logging baseline dependencies.
4. Implement `GET /` and `GET /health`.
5. Add CI steps for build and test.
6. Add contract fixture folder for API parity tests.
7. Deploy Kotlin service to a separate Render service for parallel validation.
