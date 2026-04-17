# Lessons

## 2026-04-12

- Do not lock onto a named external data source unless the user explicitly confirmed it. When the user says a category like "delivery source," keep the plan source-agnostic until the exact source is verified.
- For this project, treat Remora as the unfinished base of the institutional-footprint strategy, not as a separate unrelated strategy.
- For this repo, if something is configurable in day-to-day strategy operation, put it in a JSON config instead of hardcoding it in service logic.

## 2026-04-17

- For freshness-critical trading snapshots, do not use broad stale grace windows in the sync trigger path; they silently hide missing latest sessions.
- When calling historical day APIs, avoid using today's `00:00` as the end boundary; use next-day start (or current time) so today's published day candle can be included.
