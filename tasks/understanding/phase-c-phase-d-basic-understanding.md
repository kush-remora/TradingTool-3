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
