# Base Consolidation Low-Hit Count

The proposed screen checks whether a selected stock is repeatedly defending a similar daily-low price zone. The user selects a watchlist, and the screen evaluates each stock's latest 10 completed trading sessions. For each of those sessions, its daily low becomes a candidate support price. The screen then looks only at the 20 completed sessions immediately before that candidate session and counts how many daily lows fell within +/-1% of the candidate low. The candidate session itself is excluded from its own historical count.

V1 is intentionally a raw validation view. Each stock should show exactly 10 rows with only four data columns: stock, reference date, reference low, and historical hit count. The stock name should link to `Three-Week Stock Review + Current Week` for a deeper manual review. The view should not collapse rows, calculate a score, or hide data yet.

Highlighting is visual guidance only. Within each stock, highlight the row or rows with the highest hit count. Across the selected watchlist, order stock groups by their strongest row and highlight the stocks with the highest values, for example the top 10 stocks in a 50-stock watchlist. This makes frequent repeated lows easy to inspect without turning the highlight into a trade recommendation.

Implementation outcome: the existing watchlist scanner was repurposed for this raw view. It now loads a 60-calendar-day candle window, evaluates the latest 30 completed candles in the frontend, highlights strongest rows and the top 10 stocks, and links each stock to the existing deep-dive screen. The scanner remains non-prescriptive.

Validation completed with focused frontend tests, the linked Three-Week Stock Review tests, a production frontend build, and a core/resources Kotlin compile. Existing Ant Design deprecation and test `act` warnings remain unrelated to this change. Recommended follow-ups are to validate the raw counts on real watchlists before adding support clustering, spread/volume, or structural context.
