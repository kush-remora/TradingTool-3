## Phase D scanner UI redesign

The current `PhaseDScannerPage` works, but its structure does not reflect the real workflow clearly enough. It shows a manual Phase C ingestion screen under a Phase D label, and the UI gives equal visual weight to every field instead of emphasizing the three actual jobs on this page: import the latest candidate file, confirm pipeline health, and review the stored watchlist quickly.

The redesign should preserve the existing upload and dashboard endpoints while improving hierarchy and trading-screen usability. The target structure is: a top context header that explains what the screen currently does, compact health cards for watchlist state, a clearer upload panel with import feedback, and a denser table that groups candidate context instead of spreading every metric across many columns.

The implementation constraint is now explicit: this frontend should use Ant Design styling only for this screen. Tailwind-like utility classes created a broken render in the current app, so the page should be expressed with Ant `Card`, `Table`, `Statistic`, `Input`, `Button`, `Upload`, `Alert`, `Tag`, and layout primitives that are already active in the repo.

Follow-up finding: the upload parser was still tied to the old CSV headers and was also splitting CSV rows with plain `split(",")`. That caused quoted numeric values like `"3,410.2"` to shift columns and caused the cleaned headers such as `market_cap_bucket` and `vol_dry_200d_min_count` to stop matching entirely. The parser should stay alias-based and quote-safe so both old and cleaned CSV variants continue to import correctly.

The next consistency fix is schema vocabulary. `phase_c_watchlist` originally used several misleading legacy names like `marketcapname`, `high_252d`, `min_20d_high`, `brackets2`, and `net_profit_3q_ago`. The stack should use the cleaned names end-to-end so the CSV, database, Kotlin models, and frontend payloads all describe the same thing without mental translation. The latest clarification is that quarterly PAT should be named `net_profit_after_tax`, while the parser should still accept the older `net_profit_3q_ago` header as a fallback during transition.
