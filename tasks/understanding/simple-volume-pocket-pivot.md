# Simple volume / pocket-pivot integration

The compact stock review already computes recent volume ratios in the browser and renders price plus volume with `lightweight-charts`. The requested TradingView indicator can fit this flow if its per-session classifications are calculated in the Kotlin stock-detail response, then rendered as chart colors/markers and compact readout values. The existing endpoint already loads enough warm-up history for moving averages and pocket-pivot lookbacks, while the chart currently receives only OHLCV and delivery fields.

Phase 1 will implement the documented core signals with conservative defaults: 50-session volume average, 10-session pocket-pivot lookback, dry volume at or below 20% of the average, and Bull Snort at 3x average with a close in the top 35% of the candle and above the previous close. Backend output will include a typed signal classification and supporting values; the UI will keep the chart compact, add a legend/toggle for the classifications, and preserve the existing event/DMA overlays. This is a volume interpretation layer, not true order-flow delta or a buy/sell recommendation.

Validation will cover pure Kotlin classification edge cases, the stock-detail JSON contract, existing compact chart tests, and frontend build/test checks. Existing unrelated worktree changes must remain untouched.

Implementation is complete for the first pass. The backend now emits stable classifications and the compact chart renders/toggles them; the focused Kotlin and frontend tests pass, the frontend build passes, and the service package passes after a clean core rebuild. The repository contains unrelated in-progress changes, so the verification report distinguishes the focused feature checks from the broader build.
