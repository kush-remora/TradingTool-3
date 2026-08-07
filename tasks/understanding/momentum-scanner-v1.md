# Understanding: Momentum Scanner v1

The next feature is a daily momentum shortlisting view for the maintained Groww/NSE watchlist. It will reduce the watchlist to names worth manual attention by showing objective evidence: whether price is above the 200 DMA, the latest four weekly returns, and significant high-participation events from roughly the last 90 days. It is a research aid only; it must not produce buy/sell recommendations, an overall momentum score, AI interpretation, or order actions.

The first implementation should reuse the existing daily-candle/watchlist paths and follow the current Kotlin + Dropwizard backend and React frontend conventions. Each row must remain explainable and reproducible for an explicit as-of date. Missing or insufficient candle history should be visible in the response rather than silently dropping a stock. The UI should make 10–20 candidates easy to compare and allow the investor to drill into the underlying dates and values before doing fundamental and chart review.

## Working decisions

- Universe: the maintained Groww/NSE watchlist, consistent with the existing momentum strategy sequencing note.
- Trend evidence: current close, 200 DMA, distance from 200 DMA, an explicit above/below flag, the highest high across the latest 252 trading sessions, and distance from that 52-week high.
- Momentum evidence: four completed trading-week returns, calculated from weekly closes and displayed individually in chronological order.
- Participation evidence: daily volume divided by the prior 10 trading-session average volume; retain every event in the last 90 calendar days meeting a configurable default threshold of `>= 2.0x`.
- Event fields: date, close, traded volume, volume ratio, delivery percentage, daily close-to-close return, and price change since the event date.
- Participation lookback: 90 calendar days, shown explicitly in the response and UI.
- No ranking score. If ordering is needed, use a transparent field such as most recent participation date or volume ratio, not a composite score.
- As-of behavior: never use candles after the requested as-of date; default to the latest available completed trading day.
- UI integration: do not create a separate top-level scanner page for v1. Add compact momentum evidence fields to the watchlist, and show the detailed evidence inside the existing Three-Week Stock Review for the selected symbol.
- Navigation: selecting a watchlist candidate should open or deep-link to the existing Three-Week Stock Review with the symbol already selected.
- Backend boundary: calculate the evidence once in a reusable scan/detail service; the watchlist consumes compact rows and the Three-Week Review consumes the selected symbol's expanded evidence.

## Resolved implementation decisions

- The watchlist review opens with all rows plus an explicit `Above 200 DMA` filter and a `Showing X of Y stocks` count. This keeps incomplete rows visible while making the basic shortlist one click away.
- `2.0x` is the initial participation threshold, represented as a named backend constant so it can be tuned during historical validation. The UI calls these "high-volume days" and explains that each day reached at least 2.0x the prior 10-day average volume.
- 52-week high uses the latest 252 available trading sessions and measures distance using the current close versus the highest daily high in that window.
- Price-since-event is event close to current close and is labelled `Since event` in the detailed table.

## Implementation sequence

1. Add pure, unit-tested calculations for 200 DMA, four weekly returns, volume ratio, and participation-event extraction. **Complete.**
2. Add a backend request/response model and watchlist scan service with explicit as-of and missing-data statuses. **Complete.**
3. Add REST responses for compact watchlist rows and expanded selected-symbol evidence, reusing the same calculation model. **Complete.**
4. Add compact evidence columns/filtering to the watchlist and link candidates to the existing Three-Week Stock Review. **Complete.**
5. Add a “Momentum Evidence” section to the Three-Week Stock Review, keeping weekly structure, delivery, notes, and raw daily data as the primary review flow. **Complete.**
6. Add navigation, frontend tests, backend tests, and a full build/test verification pass. **Complete.**

## Validation outcome

The focused frontend suite passed with 19 tests, the momentum calculator suite passed with 3 tests, the frontend production build passed, and the Kotlin `resources` + `service` package build passed. Existing Ant Design and Kotlin compiler warnings remain outside this feature.

## Out of scope for v1

- Momentum score or ranking model.
- Buy/sell/hold language, AI interpretation, or Wyckoff classification.
- MACD, Bollinger Bands, RSI variants, or a large indicator set.
- Automated execution, portfolio construction, alerts, or fundamental/news ingestion.
- Backtesting quality claims until the raw scanner output has been validated on historical data.

## Why this UI boundary

The watchlist answers “which stocks deserve attention?” and the Three-Week Stock Review answers “what is the raw evidence for this stock?”. A separate scanner page would duplicate the first question and add navigation without improving the review workflow. A dedicated page can be added later only if the watchlist needs more advanced filters, date selection, or historical scan runs.
