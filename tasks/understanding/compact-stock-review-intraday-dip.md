# Compact stock review: intraday dip visibility

The compact stock review already shows daily OHLC, close position, range, and open-to-close movement. The missing intraday execution signal is the maximum downside from the session open to the session low. This should be visible for the latest session without requiring the user to mentally compare two cells.

Implement `openToLowPct` as the signed percentage change from open to low, plus a signed rupee delta in the UI. Show it as `Dip O→L` in the primary review strip and as an `Open → Low` column in Recent tape. Keep the calculation derived from the existing daily candle, add focused tests, and include the value in the Markdown snapshot/daily export. Do not add trading recommendations or thresholds.

Implemented on 2026-08-12. The primary strip uses live-session open/low when the live feed is available and falls back to the latest daily candle after hours; Recent tape remains historical daily evidence. Focused validation passed: 9 tests and the frontend production build. The full frontend suite passed 158/162 tests; four unrelated existing page tests timed out at the default 5-second limit.

Follow-up: the same primary strip now shows the current price in bold as `LTP`, with `Live feed` or `Latest close` indicating the source.
