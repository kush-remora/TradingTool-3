# Compact Review Chart View Toggle

## Why

Compact stock review needs a quick way to inspect the same price history as either OHLC bars or a cleaner close-price line without leaving the review console.

## Implemented

- Replaced the fixed candlestick price series with a selectable Lightweight Charts Bar or Line series.
- Kept Bar as the default so the existing OHLC-first review remains the initial view.
- Kept volume histograms, signal coloring, event markers, DMA guides, crosshair readout, and the 60-session opening range unchanged across both views.
- Added compact accessible Bar/Line buttons beside the existing signal controls.
- Added regression coverage for the default bar data and switching to close-price line data.

## Key decisions

- The line view plots daily close values only; the existing hover readout remains the place to inspect OHLC details.
- The chart is recreated when the view changes so Lightweight Charts receives the correct series type and data shape without introducing a second persistent price series.

## Validation

- `npm run test:run -- src/pages/compactStockReview/CompactStockChart.test.tsx`: 5 tests passed.
- `npm run build`: passed; the existing Vite large-chunk advisory remains.
- `git diff --check`: passed.
- The broader compact-review suite passed 12 tests and retained two unrelated existing failures in date/header assertions.

## Next

- Review the Bar/Line density in the live compact console and adjust button wording only if the chart control reads ambiguously at the target viewport.
