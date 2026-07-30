# Three-week stock review

Kush wants a compact, selectable-stock view for the latest three completed calendar weeks. It must show daily date, weekday, open, close, low, and high, plus one weekly summary per week: the weekly low and its date/day, weekly high and its date/day, and percentage range. This is an observation tool for judging whether price is contracting into a possible Wyckoff accumulation base; it must not label a base automatically.

The existing `/api/stocks/by-symbol/{symbol}/detail?days=21` endpoint already supplies the required daily OHLC data and `InstrumentSearch` already provides NSE-equity selection. The implementation is therefore frontend-only: a new `ThreeWeekStockReviewPage` derives the latest three available ISO-week summaries locally and displays only the associated daily rows. It adds no persistence or API contract. Focused grouping and selection tests, plus the production frontend build, passed on 2026-07-28.

Kush chose a raw-data-first experience over derived signals. The screen intentionally keeps only the selector, weekly low/high/range summary, and daily OHLC table. It shows the three preceding completed weeks plus the latest/current week, requesting 30 sessions to tolerate market holidays. The weekly summary provides the essential starting points for manual analysis without labelling the stock’s trend or base state.

The raw price review remains a single table with Date, Day, Open, Close, Low, and High. It adds only two derived columns: daily percentage change from open to close and accumulated weekly percentage change from the first available session open. Positive values are green and negative values red. The first available session intentionally becomes the base if Monday is a market holiday. The existing endpoint supplies all inputs, so the change remains frontend-only.

The stored HFCL detail data stopped at 2026-07-24, while the live quote stream had the newer 2026-07-27 session. The detail endpoint refreshes the selected symbol's missing daily-candle range from Kite, then appends the newer live-session OHLC row when the daily provider has not yet finalised that candle. If Kite is unauthenticated, it preserves the existing stored-data response rather than fail the review.

The raw table offers a compact default view for three completed weeks plus the current week. A button below that table can expand it to approximately three calendar months (70 trading sessions) for deeper historical review, with the same daily/weekly percentage calculations and weekly extreme highlights.

## Live-market and calculator enhancement — 2026-07-28

The existing shared `LiveMarketWidget` now appears above the historical review once an NSE stock is selected. It receives the selected symbol in the same `NSE:` form used by other screens, so it subscribes to the shared SSE feed only while the equity market is open and retains the existing detail drawer behaviour.

A compact, one-line buy/sell/percentage calculator now floats at the lower-right of the browser viewport, outside the page layout, so it remains in the same visible place while all review content scrolls. The live widget uses its `wide` mode to show the full quote fields directly on the page. Entering buy and sell calculates the percentage; entering buy and percentage calculates sell; entering sell and percentage calculates buy. Invalid non-positive prices and a -100% change do not trigger a dependent calculation. The change is frontend-only; focused page tests and the production frontend build passed.

URL preselection now resolves a requested base symbol to its listed NSE equity variant when needed. For example, `?symbol=STLTECH` automatically selects the currently listed `STLTECH-BE` instrument instead of leaving the selector empty. Exact trading-symbol matches still take precedence. The focused page test and production build passed on 2026-07-28.

The shared, per-instrument note store is deliberately named `public.notes`, not `stock_research_notes`. It contains an identity `id`, a non-unique `instrument_token`, `notes`, `created_at`, and `updated_at`; it supports multiple notes per stock without coupling notes to an executed trade.

## Five-session delivery context — 2026-07-28

Add a compact table beside the selected stock's existing wide live-market widget. It will show the five latest stored delivery sessions: trading date, delivery percentage, delivered quantity, and traded quantity. The values remain post-market data and deliberately do not change live-market or Wyckoff interpretation rules.

The existing stock-detail endpoint will include these rows using the already-bound delivery read DAO. This keeps the screen to one request and one explicit response contract, avoids a second UI loading path, and treats unavailable delivery values as missing rather than fabricated. Verification will cover the frontend table, Kotlin compilation, the focused frontend suite, and the production frontend build.

Implemented as described: `delivery_days` returns the latest five stored NSE delivery records for the selected instrument and the page renders them beside the live-market widget. Focused frontend tests (10), the resources reactor compile, and the production frontend build passed on 2026-07-28.

## Density refinement — 2026-07-28

The review surface should keep attention on live price and price structure. The calculator stays fixed but loses its title and uses narrower input fields. Notes leave the main content flow for a small floating note button beside the calculator; its popover retains writing, saving, reading, and deleting notes without consuming screen height.

Delivery context remains visible beside Live Market but becomes a narrow five-row table with `Date`, `D%`, and one compact `Delivered / Traded` quantity column. It must use materially less width than the wide live-market block and preserve the existing API/data behaviour.

Implemented: the calculator is title-free and narrow; notes are accessible only through an adjacent floating button and popover; and delivery uses the requested three-column compact layout. Focused tests (11) and the frontend production build passed on 2026-07-28.

## Note database mapper fix — 2026-07-28

The note query fails because JDBI's constructor mapper cannot reliably match Kotlin constructor parameters to PostgreSQL result-set labels. Replace it with the project-standard explicit row mapper, read the database's native snake_case columns directly, and validate the mapping with a focused test. This leaves the note API and schema unchanged.

Implemented with `NoteMapper` for both note list and insert-returning results. `NoteMapperTest` and the resources reactor compile passed on 2026-07-28.

## Existing research notes in review header — 2026-07-29

Show a selected stock's existing research notes beside the Fundamentals panel in the review header. Each entry should remain compact and display its newest-first number, note text, and created date. Reuse the existing per-instrument notes endpoint and keep the floating notes control as the single place for adding or deleting notes. The frontend will own the shared note state so a saved or deleted note immediately updates both the header list and the popover without a duplicate request or a new backend contract.

Plan: extract the existing note fetch/mutation state into a small hook; render a dense read-only list in the review header; adapt the floating editor to consume that shared state; add focused tests for list content and mutation refresh; then run the focused frontend suite and production build.

Implemented as planned: selected-stock notes appear as a scrollable 10–11px list with `1.` numbering, text, and an IST created date. `useInstrumentNotes` centralizes loading, saving, and deletion, so the list and floating editor stay synchronized. The focused frontend suite passed 13 tests and the production frontend build passed on 2026-07-29.

## Intraday low/high moves — 2026-07-29

Keep the existing chronological daily rows and close-versus-open `Daily %` column. Add compact `Low %` and `High %` columns that express each session's low and high relative to its opening price. This gives the requested intraday downside/upside mental model: low moves are red and high moves are green, with no interpretation or signal labelling added.

Implemented: the table now shows `Low %` immediately after Low and `High %` immediately after High. A focused test verifies a 100 open, 95 low, and 110 high renders as red `-5.00%` and green `+10.00%`. The page test suite (12 tests) and production frontend build passed on 2026-07-29.

## HFCL four-week review correction — 2026-07-31

The compact review must continue to show the three preceding completed trading weeks plus the latest/current week (four ISO-week groups total). Its 30-session request already provides enough data, including around market holidays. The `High %` column was incorrectly expressed relative to the opening price; it must instead show the session's full intraday move from low to high: `(high - low) / low * 100`. `Low %` remains the move from open to low, and `Daily %` remains close versus open.

Plan: retain the existing four-week grouping, make the `High %` calculation explicit with the low as denominator, update the focused assertion, then run the page test suite and production frontend build. The Kotlin review gate will confirm that no Kotlin code is affected.

Implemented and validated: the compact four-week grouping remains unchanged, and `High %` now shows `+15.79%` for a ₹95 low to ₹110 high session. The focused review-page suite passed all 12 tests, and the production frontend build passed on 2026-07-31. The Kotlin review gate found no Kotlin changes or Kotlin-specific concerns. The frontend build continues to emit its existing, unrelated duplicate-`title` and bundle-size warnings.
