# Forward accumulation confirmation timeline

The replay remains the source of saved base snapshots. Its stock timeline must also show all Phase D, Fresh Breakout, and Chartink "new 52-week high" dates that fall inside the run's effective `fromDate`–`toDate`; it must not show later dates. A stock's current curated-watchlist memberships should be visible as badges only.

The existing 52-week-high scanner reads `manual-input/Backtest 52 week high first time.csv` and currently writes only a report file. Its signal dates will be imported into `chartink_scan_events` as `FIFTY_TWO_WEEK_HIGH` evidence when that scanner job runs. This reuses the same CSV without adding an eighth upload slot. Re-running the scanner replaces its imported 52-week-high evidence scope.

Each base row receives a six-month evidence lane from raw Chartink evidence at read time: Accumulation, Phase D, Fresh Breakout, and new 52-week-high dates from the six months ending on the run's effective end date. The lane is deliberately independent of the selected replay period so one-month runs still have comparable historical context. It does not require rerunning the shape replay. The base eye icon opens a new tab scoped to that base's chain start/end dates.
