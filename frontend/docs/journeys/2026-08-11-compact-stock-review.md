# Compact Stock Review

## Why

The existing Three-Week Stock Review is retained as a detailed comparison reference, but it requires substantial scrolling and repeats evidence across large sections. The new UI tests a trading-terminal approach: one stock's chart, recent tape, four-week structure, evolving story, and note entry remain visible together so the daily review can be completed from one screen.

## Implemented

- Added a compact watchlist review navigator directly below the stock identity: choose a watchlist, move with previous/next arrows, and keep the current position visible as `n/total`.
- Preserved independent stock search. A searched stock outside the selected watchlist is shown without forcing it into the list; arrows can re-enter the selected list.
- Persisted `symbol` and `watchlist` in the URL so a review can be refreshed or shared without losing context.

- Added `/console/compact-stock-review?symbol=...` and the **Compact Stock Review (New)** menu entry.
- Reused the existing stock detail, live market, instrument search, momentum-event, delivery, and instrument-note APIs.
- Added a 150-session Lightweight Charts candlestick and volume view with volume-event markers and 100/200 DMA references.
- Set the initial logical chart range to the latest 60 sessions while keeping all 150 sessions loaded for pan and zoom.
- Added a two-line chart readout that defaults to the latest session and follows the hovered candle, showing OHLC, volume, prior-5-session volume comparison, delivery, open-to-close change, and low-to-high range.
- Switched the chart from magnetized to normal crosshair mode so the pointer and price guide move freely both horizontally and vertically.
- Added the existing stock-detail 100 DMA as a blue dashed price guide beside the purple 200 DMA, with both labels in the chart legend.
- Added `RSI14` and `ROC 9D` to the hovered-day readout. RSI uses the daily detail value; ROC9 is derived from the close nine sessions earlier and stays unavailable during warm-up history.
- Replaced the prior location strip with a compact four-week flow: W−3, W−2, W−1, and WTD weekly low/high bars, the weekday/date of each week's low, a 1% adjacent-low floor-alignment check, and concise HL/HH or LL/LH structure labels.
- Moved the data date into the stock identity block as a weekday plus date and removed the standalone Data column.
- Added delivery context beside the current percentage: the prior 10-session average, current-to-average multiple, and a Stable/Erratic label.
- Added the legacy three-field buy/sell/change calculator as a small right-aligned utility strip above the compact console, keeping it visible without covering evidence.
- Reworked the four-week section into one compact row per week with dedicated Week %, Low, High, Volume, Delivery, and Day % columns; each paired metric shows its low-day (`L`) and high-day (`H`) value.
- Added plain-language baseline cues to Volume and Delivery: below `0.8×` is `quiet`, `0.8–1.2×` is `near avg`, and above `1.2×` is `active`; Delivery retains its absolute percentage alongside the baseline multiple.
- Simplified the visible weekly cells after review feedback: removed participation labels and low/high relationship cues, added raw share volume beside each Volume 10D multiple, and kept Delivery as paired low/high percentages only.
- Added the low/high range percentage as a second line inside Week %; it is green `L→H` when the low occurred first and red `H→L` when the high occurred first.
- Narrowed the weekly matrix to roughly 70% of the console width while keeping Recent tape full width below it; reduced minimum column widths to avoid an internal horizontal scroll.
- Changed the compact Recent tape from 5 to 10 sessions. Its columns are Date/day, Open, Close, Day % / L→H %, Volume / vs 10D, and Delivery; the former Spread label was removed because it is represented by L→H %. The toggle still expands into the latest 30 sessions with separate High and Low columns.
- Highlighted daily rows that created a weekly low or high with subtle row tints and explicit `W low` / `W high` markers; the markers also appear when the 30-session tape is expanded.
- Tightened Recent tape spacing by sizing the table to its content instead of stretching columns across the full panel; the default 10-day view now always includes Open, High, Low, and Close, while expansion only adds rows.
- Placed Four-week structure beside Recent tape on desktop at a balanced roughly 46% / 54% split; the panels stack again below the desktop breakpoint.
- Added a four-row Top volume days table beneath Four-week structure, selecting the four largest raw-volume sessions from the latest 40 days and showing Date/day, Volume, O/H/L/C, Day %, and Delivery.
- Replaced the repeated 5-day story block with a focused Observation log: a Take note composer, existing notes with the date taken, and a compact OHLC/Day %/Volume/Delivery evidence table for each note.
- Moved the buy/sell/change calculator into the shared application header so it is available across routes; removed the compact-page toolbar copy and the legacy floating duplicate.
- Added a stock icon beside the compact review identity that opens the matching NSE instrument's Kite chart in a new tab.
- Pinned the shared calculator to the viewport's top-right so it remains available while long review pages scroll.
- Added a compact Move column to the first row showing price change from 20, 40, and 60 sessions ago to the current price.
- Moved the Move metric into a dedicated second header row so the primary evidence row stays compact and the lower row can hold future data points.
- Added a compact Fresh breakout block to the secondary header row, showing the latest close-confirmed breakout date for 20D, 50D, 52D, and 100D horizons.
- Matched the breakout block to Move: one narrow column with one horizon per line and full calendar years, avoiding four wide horizontal blocks.
- Preserved `/console/three-week-stock-review` unchanged for side-by-side comparison.

## Decisions

- Kept the existing detail endpoint contract and extended its response assembly so each requested daily row carries its RSI14 value; no new endpoint or persistence model was needed.
- Highlighted only abnormal volume and directional evidence while retaining neutral context.
- Used deterministic price-volume language and explicit next conditions; the UI does not declare a buy signal.
- Labelled notes as the latest saved observation because the current note contract stores creation time, not a dedicated reviewed market-session date.
- Defined chart volume comparison as the current session's volume percentage of the five immediately preceding sessions' average; this keeps the baseline historical and prevents look-ahead bias.

## Validation

- `npm run test:run -- src/pages/compactStockReview/CompactStockChart.test.tsx src/pages/compactStockReview/CompactStockReviewPage.test.tsx src/pages/ThreeWeekStockReviewPage.test.tsx`: 24 tests passed, including the crosshair-to-session readout behavior.
- Latest history/RSI validation: compact chart/page tests passed (5 tests), frontend production build passed, and `mvn -pl resources -am -DskipTests compile` passed.
- `npm run build`: passed; the existing Vite large-chunk advisory remains.
- `git diff --check`: passed.
- Live NETWEB inspection at 1280×720: the complete new console fit in one viewport with the existing sidebar expanded.
- Clean browser session: no runtime warnings or errors.
- Code review: 0 critical, high, medium, or low findings after corrections.
- Live NETWEB chart inspection confirmed the readout fits inside the chart at 1280×720 and exposes the requested latest-day values without increasing page height.
- Live interaction check held the selected 08 Jul candle while moving the horizontal price guide from ₹4,830.49 to ₹3,957.82; the clean session reported no browser warnings or errors.
- Live NETWEB inspection confirmed both `100 DMA` and `200 DMA` labels and guides render together; the clean session reported no browser warnings or errors.
- Focused chart tests now cover RSI display and ROC9 availability after nine sessions; the production build remains green.
- Extended the compact detail request and chart window from 70/60 sessions to 150 sessions. The backend already clamps detail requests at 200 and now populates each returned daily row's RSI14 from its full-history calculation.
- Added a focused regression assertion that the chart opens on the latest 60 sessions when longer history is returned.
- Added focused weekly-flow coverage for low alignment and higher-low/higher-high structure.
- Added focused delivery coverage using the previous 10 sessions and a coefficient-of-variation threshold for the Stable/Erratic state.
- Reused the calculator's existing two-values-in, third-value-populated behavior and verified the non-overlapping placement in the live browser.
- Added weekly-row coverage for low/high dates, signed open-to-close week movement, low/high delivery, and low/high day movement; live NETWEB inspection confirmed the matrix reads across one row per week.
- Tightened weekly table padding and widths after review feedback; live NETWEB inspection confirmed the columns remain readable without overlap.
- Live API validation: `GET /api/stocks/by-symbol/NETWEB/detail?days=150` returned 150 rows from 2026-01-01 through 2026-08-11, with non-null first and latest RSI14 values.
- `mvn -f pom.xml -pl service -am package -DskipTests`: passed; the rebuilt local service was restarted and served the validation request.
- Global calculator validation: the shared header remained mounted while switching from Summary Console to Compact Stock Review, and the entered buy value persisted.
- Kite-link validation: NETWEB rendered the expected tokenized Kite URL with `_blank` target and `noopener noreferrer` protection.
- Scroll validation: the calculator stayed at viewport top `12px` with `position: fixed` after scrolling to the bottom of the compact review.
- Momentum validation: live NETWEB displayed `20D +10.5%`, `40D +5.4%`, and `60D +26.5%` from the loaded 150-session history.
- Header-density validation: live inspection showed the Move column in the secondary row, with the primary row free of the extra metric.
- Breakout-date validation: compact page test confirms all four backend dates render in the secondary row with short, year-qualified dates.
- Breakout-density validation: the focused compact page test and production build pass after matching the field to the Move column pattern.

## Next

- Collect Kush's comparison feedback before changing density, evidence selection, wording, or replacing the existing review.
