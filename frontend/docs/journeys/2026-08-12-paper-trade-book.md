# Paper Trade Book — 2026-08-12

## Why

Provide a separate paper-trading screen that lets the user record a chart conviction in seconds and review whether that read is working over time.

## Implemented

- Replaced the overloaded Trade Journal page presentation with a compact Trade Book.
- Added a two-field quick entry flow: stock search and entry price.
- Sends quantity 1 and a 5% stop automatically while retaining the existing trade API.
- Shows open positions with entry date, entry price, live current price, P&L amount/percentage, and days held.
- Added compact close and remove actions.
- Added collapsed closed-trade history so realized positions remain available without crowding the active view.
- Renamed the sidebar item from “Trade Journal” to “Trade Book”.
- Added a Paper trade action to Compact Stock Review; it pre-fills the selected stock and latest available price and submits to the same Trade Book endpoint.
- Added a narrow stacked open-position block beside Last fresh breakout in Compact Stock Review showing entry date, entry price, current P&L, and holding days.
- Moved the Paper trade action beside Take note in the Observation log and reduced it to an icon action.
- Added a small delete icon to the stacked open-position block with confirmation before removal.
- Widened the compact-review identity column and matched the secondary-row spacer so long company names remain readable without compressing the metrics.
- Split the first header column by row: the upper cell is watchlist navigation only, while the lower cell carries stock search, company name, exchange/date, and Kite link.
- Refined the split again: upper cell stacks watchlist navigation and stock selector; lower cell shows only stock identity details. The first-column width is now 280px, and stock search popups use a wider 300px menu so option labels remain readable.

## Decisions

- Keep the current backend consolidation behavior and API contract; this change is presentation and workflow focused.
- Do not show stop price, targets, notes, quantity, or invested amount in the primary table. They remain backend data, not daily scan inputs.
- If a quote is unavailable, show “Waiting…” instead of treating the entry price as the current price.
- Keep the compact review integration contextual and one-way: it creates a record, while position monitoring remains in Trade Book.
- Only an open position for the selected symbol is shown; closed history stays in Trade Book.

## Validation

- npm run build — passed; Vite emitted only the existing large-chunk warning.
- npm run test:run -- src/pages/paperTradeBook src/pages/compactStockReview — 14 tests passed.
- Compact review interaction test verifies the paper-trade drawer opens with NETWEB and ₹4,855.00 prefilled.
- Compact review test verifies an existing NETWEB position renders with entry date, price, P&L, and holding context.
