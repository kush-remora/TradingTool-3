# Compact review watchlist navigation — 2026-08-12

## Why

The daily review loop should stay on one screen: select a watchlist, read the current stock, take a note, and move to the next or previous member. Direct stock search remains useful for stocks outside the selected list.

## Implemented

- Added an explicit 5D close-to-close movement line to the existing Move block, so recent direction is visible separately from the current candle and the broader 20D/40D/60D context.
- Enriched each last-fresh-breakout line with its crossed price level and current price distance from that level.
- Added a neutral `Gap` value beside the four-week low band, using the current price distance from the band’s upper edge.
- Aligned the breakout heading and values to one left edge and widened that secondary column for the level and distance fields.
- Made freshness explicit: the identity block labels the latest completed candle as `Close …`, while Delivery shows its own `as of …` date.
- Added `Effort → result` to Top volume days: the 10D volume multiple points directly to that session’s close-to-close move without assigning an accumulation/distribution verdict.
- Added an `Export .md` action that downloads the current snapshot, four-week structure, breakout context, top-volume table, observation log, and latest 60 daily sessions for AI-assisted review.
- Added a shared app-shell sidebar toggle; collapsed mode uses a 72px icon rail so dense review pages can reclaim horizontal space without changing routes.
- Added a compact watchlist selector and previous/next arrows below the stock identity.
- Shows the current position as `n/total`; an independent search is shown as `Independent` or `—` while a watchlist is selected.
- Keeps `symbol` and `watchlist` in the URL for refresh-safe review context.
- Wraps navigation at the first/last watchlist member.

## Validation

- `npm run test:run -- src/pages/compactStockReview`: 10 tests passed, including the 5D movement assertion.
- The compact page test verifies breakout levels and current distance values render beside the dates.
- `npm run build`: passed; the existing Vite large-chunk advisory remains.
