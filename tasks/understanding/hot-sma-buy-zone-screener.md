## Current Understanding

Kush wants a read-only daily screener page for existing backend universes, not a new theme-config system. The workflow is manual: open the page, select a universe from the existing universe endpoint, run the scan, and inspect one sortable/filterable table that shows every stock in that universe. The primary decision rule is simple and explicit: the buy zone is based on `SMA200` only, with `BUY_ZONE` defined as any stock whose LTP is at or below `SMA200 + 5%`. Stocks below `SMA200` are considered better, but the default ordering should still be by absolute closeness to `SMA200`.

The first version should stay lightweight and observational. Required columns are symbol, company name, LTP, `SMA100`, `% from SMA100`, `SMA200`, `% from SMA200`, `RSI 14`, drawdown from `20D` high, drawdown from `60D` high, consecutive red days where `close < previous close`, `3-day move %`, and a status label that includes `BUY_ZONE`. The table should show all stocks even when none are near the buy zone, allow sorting and filtering on each column, and use a page size of `110`.

## Implementation Outcome

The implementation reused the existing `hotsma` backend path instead of adding a second screener engine. The service now evaluates every stock in the selected universe, keeps `SMA100` and `SMA200` context, adds `RSI 14`, `20D`/`60D` drawdown from recent highs, consecutive red-day count, and `3D move %`, and classifies rows with a simple `BUY_ZONE` vs `ABOVE_BUY_ZONE`/`NO_SMA200` status. Rows are pre-sorted by absolute distance to `SMA200` with below-`SMA200` names naturally favored when distances tie.

On the frontend, the new `SMA Buy Zone` console page loads existing universe options, remembers the last selected universe, and renders a single Ant table with built-in column sorting and filtering. The table always shows the full universe and uses a fixed `110` row page size. Focused Kotlin tests and page tests passed, and the frontend production build passed. Full `resources`/`service` compile validation is currently blocked by pre-existing unrelated module errors already present in the worktree.

## Follow-up Update

The universe picker now supports multi-select. The request contract changed from a single `indexKey` to `indexKeys`, the resource layer now normalizes and de-duplicates the selected keys, and the Hot SMA scanner merges the requested universes into one result set while preserving each row's original `indexKey` for table visibility. The frontend stores the selected universes as an array in local storage and now lets Kush run the screener across multiple universes from one scan without changing the core buy-zone logic or table behavior.
