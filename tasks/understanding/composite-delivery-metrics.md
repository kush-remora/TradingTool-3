# Composite Delivery Metrics — Living Understanding

Kush wants the composite-volume pipeline to begin with one central logic module that defines how delivery quantity and delivery percentage are interpreted. This shared implementation should become the only strategy-facing source of delivery metrics, replacing duplicated delivery calculations and thresholds across the current Remora and Wyckoff code paths. Raw NSE ingestion, reconciliation, and source-validation fields remain data plumbing unless the final scope explicitly changes them.

The 2026-06-20 requirements currently favor absolute delivery quantity for strategy decisions: a buffered quantity baseline and `delivery_shock_ratio` for shared/router logic, plus a lagged median basement-session quantity baseline and `accumulation_spike` for the replacement Remora footprint. They retain `delivery_pct` as an input but do not yet define its new decision role. Before implementation, we need to settle whether delivery percentage is (a) informational/diagnostic only, (b) a centrally calculated contextual confidence signal, or (c) a hard strategy gate, and confirm which existing scanners/backtests must migrate versus be deprecated.

## Current status

- Confidence: 75%.
- Foundation to implement first: shared delivery-metrics contract/module.
- Known strategy consumers to replace: current `RemoraService` percentage-baseline ratio logic and Wyckoff Phase 1 percentage threshold/density logic.
- Likely non-strategy consumers to preserve: NSE parsing, persistence, reconciliation, and cross-source percentage validation.
- No application code changes have started.
