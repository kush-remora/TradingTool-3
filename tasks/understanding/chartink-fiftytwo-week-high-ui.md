# Chartink 52-Week High UI

## Current Understanding

Kush now wants a lightweight UI on top of the generated Chartink 52-week-high backtest report so the data can be explored inside the existing TradingTool console. The report already exists as `build/reports/chartink-fiftytwo-week-high-backtest/latest.json`, and the UI should make it easy to filter by symbol and date while also handling the fact that the same stock can appear multiple times across different signal dates.

The cleanest shape is a read-only API that serves the latest generated report, plus a frontend page that keeps filtering and grouping client-side. The page should support at least strategy selection, symbol search, signal-date filtering, and grouped-by-symbol browsing with drill-down rows for each appearance. Summary stats should react to the currently visible trades so Kush can quickly compare large-cap, mid-cap, and small-cap behavior inside any filtered slice.

## Implementation Plan

- Add a small Kotlin report reader service for `latest.json`.
- Expose that report through a new `GET /api/strategy/chartink-fiftytwo-week-high/report` endpoint.
- Add frontend types and a hook for the report payload.
- Build a console page with:
  - strategy selector
  - symbol, market-cap, sector, and outcome filters
  - signal-date range filter
  - grouped symbol table with expandable trade history
  - live filtered success-rate cards, including cap-bucket splits

## Implementation Outcome

The UI now reads the generated backtest output through a simple read-only strategy endpoint instead of rerunning the job from the browser. This keeps the backend contract small and makes the page deterministic: the job writes `latest.json`, and the UI simply explores that artifact.

The new console page supports strategy selection, symbol search, signal-date range filtering, cap-bucket, sector, and outcome filters. Repeated symbols are grouped into parent rows with an expandable table for each appearance, and the top cards recompute filtered success rates plus large-cap, mid-cap, and small-cap splits from the currently visible trades.
