# Adaptive Breakout Follow-Through Console

The adaptive-breakout console already receives each scanned stock's daily OHLC and volume history through `rawSteps`, plus the breakout date and live quote overlay. The requested view is a focused list of stocks that are already in `BREAKOUT_CONTINUATION`, combining the breakout candle with the single highest-volume session after that breakout.

The table will show two evidence rows per stock: breakout day and highest-volume post-breakout day. Both rows will show close price, volume, volume versus the preceding 10-session average, and close position within the day's range. A stock-level summary will show current price, return from breakout close to the current price, breakout date, and elapsed calendar days. The scanner response now includes compact evidence objects computed from full candle history, so older breakouts remain available without returning years of raw candles; incomplete history still renders as unavailable rather than inventing values.

Validation will cover the two-row comparison, post-breakout volume selection, current return, and the existing scanner flows. The current structural scanner table and recent-20D modal remain available below the focused breakout view.

Implementation is complete. The backend now computes compact breakout-day and highest-volume-after-breakout evidence from full candle history, while the frontend renders the comparison in a dense two-row table. The focused scanner test suite (11 tests), frontend production build, and adaptive-breakout core test suite (16 tests) pass.
