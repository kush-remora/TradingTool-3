# Stock history CSV console

The requested feature is a small, single-stock export console. Kush selects an NSE equity, chooses a 1-, 3-, or 6-month lookback, reviews the daily rows, and downloads the same rows as a CSV. The existing `/api/stocks/by-symbol/{symbol}/detail?days=` endpoint already supplies daily OHLCV candles and delivery history, so the feature should remain a frontend composition rather than introduce another service contract.

The export joins delivery data by trading date, calculates `open → high %` and `open → close %`, leaves delivery fields blank when the backend has no matching delivery record, and writes rows oldest-to-newest for analysis. Validation should cover CSV escaping, percentage calculations, delivery joins, UI selection/loading/error states, and a production frontend build.

Implemented on 2026-08-06 as a frontend-only console at `console/stock-history-download`. Focused tests and the production build pass. The full suite has two unrelated `PhaseDScannerPage` timeout failures; the new feature is covered by 3 passing tests.
