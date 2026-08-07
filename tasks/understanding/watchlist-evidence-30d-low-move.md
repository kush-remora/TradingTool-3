# Understanding: Watchlist 30-Day-Low Move Evidence

The Watchlist evidence summary should show, for every stock row, how far the current price has moved above the lowest daily low in the latest 30 completed trading sessions. A move of at least 10% is useful momentum evidence for prioritising manual review, but it must remain raw evidence rather than a recommendation or score.

The existing shared momentum evidence contract will provide the 30-session low and its close-to-low percentage. The watchlist summary will recompute the percentage from the live LTP when available, fall back to the latest close otherwise, and display a sortable `Move from 30D low` column with a `≥10% move` cue. Stocks with missing history or price data will show an em dash.

Implemented and validated on 2026-08-08. The focused frontend suite passed (3 tests), the frontend production build passed, and the momentum calculator suite passed (5 tests).
