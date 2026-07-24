# Weekly Base Definition

This screen is deliberately separate from trade execution. For each evaluation day, it uses only the three immediately preceding completed ISO weeks. It takes the lowest daily price from each week, then forms a support zone from the minimum and maximum of those three weekly lows. A zone is valid when `(zone ceiling - zone floor) / zone floor × 100` is at most 2%.

V1 analyses the latest 200 trading sessions for one selected NSE equity and presents each daily base check, including the three source weekly lows, zone range, width, and validity. A base is also valid only when the day's close is within the configured distance range around its configured moving-average window; the initial JSON configuration uses a 200-session SMA and a −15% to +15% range. It creates no trades, stores no state, and does not use current-week data when evaluating a date.
