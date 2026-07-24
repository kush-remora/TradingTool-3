# Weekly Base Definition

This screen is deliberately separate from trade execution. For each evaluation day, it uses only the three immediately preceding completed ISO weeks. It takes the lowest daily price from each week, then forms a support zone from the minimum and maximum of those three weekly lows. A zone is valid when `(zone ceiling - zone floor) / zone floor × 100` is at most 2%.

V1 analyses the latest 200 trading sessions for one selected NSE equity and presents each daily base check, including the three source weekly lows, zone range, width, and validity. A base is also valid only when the day's close is within the configured distance range around its configured moving-average window; the initial JSON configuration uses a 200-session SMA and a −15% to +15% range. It creates no trades, stores no state, and does not use current-week data when evaluating a date.

## Group backtest extension

The third screen is backtest-only. It selects existing index-constituent groups and runs the latest 200 sessions for every member independently. A trade can enter only when a valid base is touched and the same candle reaches `low × 1.01`; it holds until `entry × 1.05` or remains open at the end. Each symbol permits one open trade at a time. Results retain the originating index group, including duplicated membership when a stock belongs to more than one selected group.
