# CSV Backtest Table Filter Distortion

The Trade Details table becomes unreliable when table filters and Maximum V2 Run % are combined: filtering can appear ineffective, repeated rows appear, and the table visually distorts after entering a V2 maximum. The investigation must treat row identity, duplicate response rows, pagination, and the interaction between Ant Design's internal filters and the externally filtered `dataSource` as one system.

The fix should preserve sector search and select-all behavior, make row identity unique and stable, prevent duplicate signals from producing repeated visible trades where appropriate, and keep Maximum V2 Run % composable with table filters. Verification must cover duplicate-looking trades, V2 null/limit behavior, combined filters, and the production frontend build without touching the user's unrelated Kotlin V2 validator changes.

## Final Result

The root cause was confirmed with a six-row duplicate fixture: the backend processed duplicate symbol/date signals, and the frontend used that same non-unique pair as the React row key. After Maximum V2 Run changed the data source, React retained stale rows and duplicated others. The backend now processes one signal per symbol/date, the frontend defensively deduplicates and assigns stable row identities, and the visible count includes both the V2 maximum and selected sectors.

Validation passed: 18 CSV-backtest core tests, 5 focused frontend tests, the frontend production build, and live browser verification of Maximum V2 Run 15% combined with Healthcare. The final table showed only SUNPHARMA and correctly reported `Showing 1 of 3 trades`.

## Filtered SL Outcome Summary

The result header should also show a compact outcome line for the currently filtered Trade Details rows. It must recompute after either Maximum V2 Run % or the sector table filter changes and show filtered total, SL Yes, SL No, and success rate. Success rate is defined directly from the SL column as `SL No / filtered total`, with zero returned for an empty result.

Implemented with one shared filtered row set so the visible count and SL summary cannot diverge. Six focused frontend tests and the production frontend build passed; the build retains only the existing bundle-size advisory.
