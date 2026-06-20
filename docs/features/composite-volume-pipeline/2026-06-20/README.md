# Composite Volume Pipeline Requirement Map

## Why This Folder Exists

This folder replaces the old "current Wyckoff strategy" requirement direction with a cleaner three-layer requirement stack:

1. a shared market-metrics contract,
2. a replacement Wyckoff footprint pipeline,
3. a dependent daily high-volume router that splits movers into actionable buckets.

These docs are the new source of truth for this strategy family.

## Product Decision

- The current Wyckoff requirement direction should be treated as legacy.
- The replacement strategy keeps the `Remora` product name.
- The existing `Remora` and `Wyckoff Phase 1` implementations are not deleted yet, but future work should plan to migrate away from their current logic.
- New implementation work should start from the docs in this folder, not from the older Wyckoff notes.

## Requirement Stack

### 1. Shared Market Metrics

Document: [shared-market-metrics-requirement.md](/Users/kushbhardwaj/Documents/github/TradingTool-3/docs/features/composite-volume-pipeline/2026-06-20/shared-market-metrics-requirement.md)

Purpose:
- define one meaning for daily percentage fields,
- standardize buffered baselines and shock ratios,
- keep downstream scanners from redefining the same math differently.

This is the foundation layer. Both later requirements depend on it.

### 2. Wyckoff Footprint Replacement

Document: [wyckoff-footprint-replacement-requirement.md](/Users/kushbhardwaj/Documents/github/TradingTool-3/docs/features/composite-volume-pipeline/2026-06-20/wyckoff-footprint-replacement-requirement.md)

Purpose:
- replace the current Wyckoff accumulation requirement with a lagged, configurable, footprint-first model,
- identify Phase C style seller exhaustion / accumulation lock-up,
- keep breakout confirmation separate from footprint detection.

This is the strategy-core requirement.

### 3. Daily Volume Router

Document: [daily-volume-router-requirement.md](/Users/kushbhardwaj/Documents/github/TradingTool-3/docs/features/composite-volume-pipeline/2026-06-20/daily-volume-router-requirement.md)

Purpose:
- ingest a daily top-volume mover list,
- enrich it with shared metrics,
- rank it down to a small shortlist,
- route each relevant stock into `SHORT_CANDIDATE`, `TREND_LONG`, `WYCKOFF_BREAKOUT`, or `DISCARD`.

This requirement depends on both earlier docs.

## Recommended Build Order

1. Build the shared market-metrics contract and naming cleanup first.
2. Replace the Wyckoff footprint logic next.
3. Build the daily high-volume router after the shared metrics and Wyckoff replacement are stable.

## Key Dependency Notes

- The daily router should not invent its own `price_change_pct`, `volume_shock_ratio`, or `delivery_shock_ratio` definitions.
- The Wyckoff replacement should consume shared metrics, then produce a cleaner footprint state that later routers or dashboards can use.
- The daily router may use the Wyckoff footprint state as one classification input, but it must remain a separate workflow from the core Wyckoff detector.

## Assumptions Captured In This Pass

- "Refine how the system uses daily percentage across the board" means we should standardize percentage semantics and naming across all related screens and scanners.
- "Replace current Wyckoff strategy completely" means requirement replacement now, with code migration planned later.
- The replacement requirement should keep the `Remora` name instead of introducing a new product label.
- The top-volume workflow should persist only a ranked shortlist, not all 100 raw candidates as canonical stored output.
