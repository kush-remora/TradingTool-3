## Phase C to Phase D basic understanding

The current implementation is a manual ingestion and review flow for Phase C candidates, not yet a true Phase D execution engine. A user uploads a Chartink CSV or TSV from the frontend page labeled `Phase D Ignition Scanner`, the browser parses the rows, and the backend stores them in `phase_c_watchlist` with a resolved Kite `instrument_token`, `added_on`, `last_seen_on`, and a default `status` of `chartinkFilter`.

This means Phase 1 here is primarily about preserving a curated candidate set plus its dry-up and safety metrics so the app has a clean starting universe for later monitoring. The missing piece is the actual Phase D logic: a scheduled or on-demand engine that reads `phase_c_watchlist`, fetches current market and delivery behavior for those names, evaluates ignition rules, and updates status or emits alerts when a candidate moves from passive watchlist state into actionable Phase D behavior.
