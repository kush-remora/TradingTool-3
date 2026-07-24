# CSV Backtest Breakout-Day Move

Add an auditable trade-table metric for the signal (breakout) candle. The value is `(close - open) / open * 100`, using the candle dated `signalDate`; it is independent of the later entry strategy and therefore remains meaningful for every returned trade. The API response and frontend type will carry the value, and the table will render it as a sortable signed percentage.

Implemented in the CSV trade-response model and service, frontend contract, and Trade Details table. Validation passed: focused Kotlin CSV backtest test, CSV table tests (6/6), and production frontend build.
