# Stock history CSV console

The requested feature is a small, single-stock export console. Kush selects an NSE equity, chooses a 1-, 3-, or 6-month or 1-year lookback, reviews the daily rows, and downloads the same rows as a CSV. The existing `/api/stocks/by-symbol/{symbol}/detail?days=` endpoint supplies daily OHLCV candles and delivery history; the endpoint's display limit must support the new 365-day request without introducing another service contract.

The export joins delivery data by trading date, calculates `open → high %` and `open → close %`, leaves delivery fields blank when the backend has no matching delivery record, and writes rows oldest-to-newest for analysis. Validation should cover CSV escaping, percentage calculations, delivery joins, UI selection/loading/error states, and a production frontend build.

Implemented on 2026-08-06 as a frontend-only console at `console/stock-history-download`. Focused tests and the production build pass. The full suite has two unrelated `PhaseDScannerPage` timeout failures; the new feature is covered by 4 passing tests.

On 2026-08-15, added a `1 year` option requesting 365 daily rows and raised the stock-detail display cap from 200 to 365. Delivery fields remain populated only where the existing delivery history is available.
