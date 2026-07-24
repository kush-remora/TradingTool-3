# CSV Backtest Delivery Columns

Add two display-only delivery measures to every CSV backtest trade. `Breakout Day Delivery %` is the stored NSE delivery percentage for the signal date. `T-5 Max Delivery %` is the maximum stored delivery percentage across the five completed trading sessions immediately before that signal date.

Both fields remain nullable when delivery history is unavailable. They do not change validation, entry, exit, or position sizing. The CSV backtest will read the existing persisted delivery table once for the selected symbols and date range; it will not fetch delivery data during a run.

Implemented in the CSV backtest response and Trade Details table. Validation passed: focused delivery-metric test, Kotlin service compilation, CSV table tests, and production frontend build.
