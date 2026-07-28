# Weekly price watchlist scanner

## Understanding and plan — 2026-07-28

Kush wants an observation-first companion to Three-Week Stock Review. He should select one existing watchlist/universe, compare the latest three completed weeks plus the current week for every member in a compact 3–4-line card, then open the existing single-stock review for a chosen symbol. This is visual price-structure comparison only; it must not declare a setup or a buy signal.

The existing universe membership and daily-candle store already contain the inputs, but there was no endpoint that returned both together. A read-only backend endpoint now lists available groups and returns their members with the most recent 35 calendar days of cached daily OHLC, using a 12-request concurrency limit. The frontend reuses the existing weekly grouping utility to keep calculation logic aligned with the single-stock screen. Each scanner row now retains the weekly low/high dates with weekdays, not only the prices and range. **Open review** opens the detail screen in a new browser tab with `?symbol=<NSE symbol>`, which the review page resolves from its instrument list. Both screens use the same fixed lower-right Buy / Sell / Change calculator. Frontend interaction tests, production build, and core/resources compilation passed.
