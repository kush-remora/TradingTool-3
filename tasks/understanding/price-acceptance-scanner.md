# Price Acceptance Scanner

The proposed scanner lets the user select an index or maintained watchlist from `public.index_constituents`. For each active symbol and an as-of trading date, it uses the candle body range between the day's open and close, then counts how many prior closes fell inside that inclusive price band over the previous 20, 40, 60, 80, and 100 trading sessions.

This is a useful first-pass price-acceptance or value-area hint: repeated historical closes in today's body range may show that the market has previously accepted that price zone. It is not, by itself, proof of Wyckoff accumulation. The V1 result should expose raw counts and percentages by lookback, preserve the selected universe and as-of date, and exclude the anchor day from historical counts so today's close does not automatically create a hit. Later validation can add support/location and volume or delivery evidence.

Implemented V1 as a read-only scanner using the existing cached daily-candle path. The backend exposes universe options and a date-aware scan endpoint; the frontend adds a compact Price Acceptance Scanner screen. Rows are ranked by 100-session hit count, while all five hit counts and rates remain visible for inspection. Stocks with fewer than 100 prior sessions are retained with their available-session count shown, so the result does not silently discard newer listings.
