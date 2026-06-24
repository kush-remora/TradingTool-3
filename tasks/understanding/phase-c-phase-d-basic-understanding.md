## Phase C to Phase D basic understanding

The current `Phase D` screen is still functionally a Phase C intake-and-review workflow, not a true Phase D execution engine. A user uploads a Chartink CSV from the UI, the frontend parses the file safely including quoted values and both old/new header names, and the backend resolves each symbol to a Kite `instrument_token` before upserting the record into `phase_c_watchlist`.

This means the current flow is centered on maintaining a clean current-state candidate watchlist, not building a dated archive or running live ignition logic yet. The dashboard reads `phase_c_watchlist` and shows the latest watchlist state with token status, dry-up metrics, structure context, and quality fields. The missing next-stage capability is still the actual Phase D engine that evaluates these names for ignition behavior over time.

### Next Planned Funnel Stage

The immediate next phase is **not** ignition. The next funnel layer should take the existing three-filter shortlist from Chartink and apply a second-level Wyckoff delivery check. In plain terms: after a stock already qualifies on dry-up, 200 SMA / ROC context, price/volume filters, market-cap floor, and other base checks, the system should ask whether that candidate also qualifies on **delivery volume / delivery percentage behavior**.

This delivery gate should be treated as the next decision layer in the funnel, not as a downstream execution trigger. The intent is to improve institutional-quality filtering before any later ignition or action logic is designed.

### Delivery Filter Direction

The delivery filter should use both **delivery percentage** and **delivery volume** as a rolling-condition confirmation layer. The Wyckoff idea is: if volume was drying up and daily ATR / spread context was relatively quiet, then meaningful delivery behavior during that period may indicate that someone used the quiet zone to take conviction-based positions.

The evaluation windows should include:

- a **20-trading-day** lookback for the broader 4-week picture
- a **10-trading-day** lookback for the more recent picture

If a stock passes the existing step-1 shortlist but fails the delivery rule, it should **remain in the watchlist**. The system should simply mark the delivery filter as not passed, because the same stock may remain structurally valid and pass the delivery condition later.

### Phase Naming Clarification

The current delivery-validation layer should be treated as **Phase 2** in the practical product funnel:

- **Phase 1:** base structural shortlist from Chartink and existing quiet-context checks
- **Phase 2:** delivery validation on top of that shortlist

The quiet-range / low-ATR check is already part of Phase 1 and should not be duplicated inside Phase 2. Phase 2 should focus only on proving whether real delivery-based conviction appeared during that quiet setup.

### Phase 2 UX / Execution Decisions

Phase 2 delivery validation should be runnable on demand from the UI, not only during CSV upload. The page should include a button to re-run the delivery-validation pass on the current watchlist so the same stocks can be evaluated again against refreshed delivery data without needing a new upload.

The dashboard should be split into multiple tabs:

- **Tab 1:** all Phase 1 stocks
- **Tab 2:** only stocks where delivery conviction is present

Both tabs should use the same column structure so comparison stays easy, with Tab 2 simply showing the narrower conviction-qualified subset.

For the first implementation, market-cap-specific delivery thresholds are **not** required for Phase 2. Use one shared threshold set for all caps until real examples justify tiered tuning.

### Phase 2 Implementation Status

Phase 2 is now implemented as an on-demand delivery-validation layer on top of the same `phase_c_watchlist` rows. The backend reads the current watchlist, loads recent delivery plus daily-candle history, computes a basement-price delivery baseline, checks delivery spike together with supportive delivery %, and writes the latest status back onto the same symbol row.

The dashboard now supports:

- a `Run Delivery Validation` button
- `All Phase 1 Stocks` tab
- `Delivery Conviction` tab
- Phase 2 fields such as latest delivery quantity, delivery %, baseline DQ, spike ratio, and 10d/20d conviction counts

This remains a **current-state watchlist**, not a historical evaluation archive. Re-running Phase 2 overwrites the latest delivery-validation fields on each symbol rather than storing dated evaluation history.

### Recent Fixes & Current State (June 2026)

- **UI:** Redesigned the Phase D UI using Ant Design only.
- **Naming:** Cleaned CSV, internal, and schema naming.
- **Parsing:** Fixed null persistence caused by old header matching and broken CSV splitting.
- **Schema:** Renamed the PAT field to `net_profit_after_tax`.
- **Dashboard:** Fixed dashboard DB mapping so `/api/strategy/phase-c/dashboard` works again.

### Current Behavior to Remember

- The `phase_c_watchlist` table is a **current-state watchlist**, not a historical daily archive.
- If the same symbol is uploaded again, it updates the existing row rather than creating a new dated history row.
- The Dashboard reads this table and shows the current watchlist with token status, dry-up metrics, structure context, and quality fields.

### Current Working State (2026-06-24)

The current live flow is now "Phase 1 shortlist review first, then Phase 2 delivery validation on demand" on the same Phase D page. The backend analyzer, API route, dashboard summaries, and the two-tab UI are already implemented and the focused backend and frontend tests still pass after the latest local edits.

The current uncommitted local changes narrow to three things: the analyzer now keeps zero-baseline cases inside `NOT_PASSED` with a `zero_baseline` reason instead of returning `DATA_MISSING`, the default delivery-quantity spike threshold was relaxed from `1.5` to `1.0`, and the frontend wording now says `DQ Ratio` / `DQ Hits` for those delivery-count columns. One mismatch remains: the export metadata text in `PhaseCWatchlistService` still describes the rolling hit counts as `>= 1.5x of base`, so that description is now stale relative to the active config.

### Next Task: Manual Output Validation Loop

The next task is not new feature work. It is a validation pass on the current Phase 1 plus Phase 2 output so each stock can be manually reviewed and any bug, misleading label, bad threshold, or logic gap can be fixed from real examples rather than assumptions.

Working loop:

- review one stock at a time from the current watchlist output
- compare the displayed result against the expected Wyckoff interpretation
- classify each issue as one of:
  - data bug
  - UI wording / explanation bug
  - threshold tuning issue
  - logic bug
  - missing context / observability
- fix only the proven issue, then re-run the smallest relevant test set

Important guardrail: manual review findings should drive small targeted corrections, not a broad redesign. If repeated examples show the same confusion pattern, that becomes evidence for a rule or UX refinement.

### Validation-Driven Tuning Update

The first manual review case (`CASTROLIND`) exposed that the previous `60`-trading-day Phase 2 base window was probably too stale for the current quiet-setup question. The baseline could stay inflated by older heavy-delivery behavior and make recent conviction look weaker than it should.

The active config was therefore tightened from a `60`-trading-day base window to `40` trading days so the base stays closer to an approximately 2-month setup. This is a deliberate tuning change, not a bug fix to the arithmetic itself. The export description for delivery hit counts was also updated so it no longer claims the older `1.5x` wording while the active local threshold is `1.0x`.

### Filtering Need From Manual Review

The next usability gap is list reduction during manual review. The current table shows the full current watchlist plus the Phase 2 status, but it does not yet let the user quickly narrow to the most interesting quiet-setup names when the important signals live inside grouped cells rather than one flat sortable numeric column.

The user specifically cares about names that are structurally quieter first, especially quarter-low-volume style behavior, and then wants to prioritize those names further by repeated delivery support or repeated delivery-hit counts. This suggests the right UX is not per-column table filters on the grouped cells, but a small dedicated review filter/sort panel that can rank and narrow the watchlist using a few high-signal criteria.
