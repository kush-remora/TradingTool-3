# Groww API Probe POC

## Feature name and why it was built
Groww API Probe POC. This was built to prove we can fetch Groww website stock/news data on demand for manual daily review workflows before wiring it into the app UI.

## What was implemented
1. Added a small typed `tsx` script that calls the Groww company and stock-news endpoints for a given `searchId` and `growwCompanyId`.
2. Saved the fetched payloads and a short summary to `build/reports/groww-api-probe/`.
3. Added optional cookie/auth/extra-header injection so the same probe can be reused if a later endpoint turns out to require the logged-in browser session.
4. Documented the current finding that the two sample endpoints work without reusing Chrome login cookies.

## Key decisions and tradeoffs
1. Kept the POC outside the Kotlin backend for now because the current goal is endpoint validation, not durable service integration.
2. Avoided browser automation because the public sample endpoints already respond directly, which is simpler and more reliable.
3. Kept the output artifact-backed so daily inspection and debugging stay easy while the Groww surface is still exploratory.

## Validation run and outcomes
1. `npm run groww:probe` should return `HTTP 200` for both sample endpoints and write `company.json`, `news.json`, and `summary.json`.
2. The manual pre-check already showed both sample endpoints responding with JSON directly from the terminal on 2026-06-11.

## Next follow-ups
1. Decide whether the daily app button should call a local backend endpoint or shell out to the probe script.
2. Add a backend wrapper only after the exact daily news workflow is finalized.
3. Test one clearly authenticated Groww endpoint later to see whether copied Chrome headers are enough or whether browser automation is needed.
