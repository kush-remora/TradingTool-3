# Lessons

## 2026-04-12

- Do not lock onto a named external data source unless the user explicitly confirmed it. When the user says a category like "delivery source," keep the plan source-agnostic until the exact source is verified.
- For this project, treat Remora as the unfinished base of the institutional-footprint strategy, not as a separate unrelated strategy.
- For this repo, if something is configurable in day-to-day strategy operation, put it in a JSON config instead of hardcoding it in service logic.

## 2026-04-17

- For freshness-critical trading snapshots, do not use broad stale grace windows in the sync trigger path; they silently hide missing latest sessions.
- When calling historical day APIs, avoid using today's `00:00` as the end boundary; use next-day start (or current time) so today's published day candle can be included.

## 2026-04-18

- When the user explicitly asks for review/analysis first, do not start implementation. Produce a decision document with impact/risk/options, get sign-off, then code in a separate step.

## 2026-04-19

- In backtests, never use `entryPrice` as the default fallback exit price when current-rank data is missing; first attempt exit-day candle close, otherwise clearly mark pricing as unavailable.
- When result windows are user-selected but computed from sparse historical snapshots, always show both requested range and actual data-coverage range in UI to avoid false inconsistency.
- When adding frontend universe filters, ensure all action endpoints (including refresh/sync) carry the same universe parameter; otherwise the UI selection and backend refresh scope drift.

## 2026-04-21

- For earnings-event tracking in this single-user tool, prefer a single-table JSONB payload (`earnings_results.behavior_payload`) over a separate snapshot-history table unless the user explicitly asks for normalized history tables.
- For cron-backed external data sync (Groww earnings/watchlist), never swallow upstream HTTP/parse failures as empty lists; fail fast so scheduler health checks catch freshness/auth regressions.
- When the user prefers manual API copy/paste for reliability, switch cron ingest to file-based adapters instead of spending cycles on brittle authenticated scraping.

## 2026-04-23

- In manual-symbol trading flows, do not rely on local `stocks` table membership for eligibility; always resolve instrument tokens from Kite exchange instruments first so valid NSE symbols like `ARE&M` are never dropped before strategy evaluation.
