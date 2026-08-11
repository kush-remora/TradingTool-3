# Three-Week Stock Review UX

This console should become a longitudinal stock-review workspace, not a passive multi-stock report. The daily job is: find or advance to one stock, read the prior observation, inspect today's price/volume/delivery evidence and compact chart, then append a short personal observation that continues the stock's story.

The current screen devotes most attention to navigation, repeated summary facts, and large evidence tables. The full NETWEB page repeats current price, 200 DMA, 52-week high, weekly returns, volume events, and weekly structure across several cards, while the daily table has many wrapped columns and ambiguous full-row red/green shading. There is no visible chart or usable note/observation composer. The sidebar can be treated separately as a collapsible shell.

Corrected direction: this is not a navigation or organization problem. It should behave like a dense trading terminal where one stock's complete recent story is understandable in one viewport without tabs, drill-downs, or multi-page navigation. Use a compact stock/price strip, a 60–90 day price-volume-delivery chart, today's tape and prior-day comparison, a four-week structure matrix, important volume events, yesterday's observation, and today's note simultaneously on screen. Highlight only exceptional evidence; preserve the rest as compact context. The goal is a 20–30 second skim followed by one written observation.

Implementation scope approved on 2026-08-11: preserve the existing `/console/three-week-stock-review` page unchanged and add a separate `/console/compact-stock-review?symbol=...` comparison route labelled **Compact Stock Review (New)**. Reuse the existing frontend-only stock-detail, live-market, instrument-search, and instrument-note APIs. The first version implements the approved one-screen terminal with a 150-session candlestick/volume chart, compact story/evidence panel, four-week structure, five-session tape, previous note, and inline note creation. No existing-route behavior changes are in scope.

Implementation outcome: the separate compact route is available and the original review remains unchanged. The chart now shows 150 sessions (roughly seven months of trading days), volume, participation-event markers, 100/200 DMA lines, and per-day RSI14/ROC9 readouts; the first row also exposes a raw W−2/W−1/WTD flow with floor alignment and HL/HH or LL/LH structure labels. The adjacent story shows the latest saved observation, current price-volume evidence, a deterministic effort-versus-result reading, the next condition, and inline note saving. Focused tests, the production frontend build, whitespace checks, live layout inspection, and a clean-session browser log check passed.

Chart refinement requested on 2026-08-11: the chart itself should expose the selected candle's evidence in its top-left corner. Default to the latest session and follow the crosshair on hover. Show date, open, high, low, close, volume, volume as a percentage of the prior five-session average, delivery percentage, open-to-close change, and low-to-high range. Keep this readout inside the existing chart footprint so the one-screen density is preserved.

Refinement outcome: the chart readout is implemented inside the existing chart footprint and uses the five sessions immediately preceding each candle for the volume baseline. It was verified with a focused hover-interaction test, the compact and original review regression tests, a production build, and live NETWEB inspection at 1280×720.

Interaction correction: the chart crosshair must move freely on both axes. Candle selection remains horizontal—determining which day's OHLCV evidence appears in the readout—but the horizontal price guide must follow the pointer vertically rather than snapping to the candle's close.

Moving-average refinement: show both the existing 200 DMA and the stock detail's 100 DMA on the same chart, with distinct dashed colors and labels. No additional API work is required because `sma100` is already part of the stock fundamentals response.

Momentum refinement: add the selected day's RSI14 and 9-day ROC to the same hover readout. Use the existing daily RSI field and derive ROC9 from the close nine sessions earlier; show an unavailable dash until the lookback exists.

History-window refinement: request and render 150 daily sessions for the compact chart instead of the previous 70-day/60-session window. The existing backend detail endpoint already permits up to 200 sessions and loads sufficient calendar history, so this is a bounded request plus response assembly validation rather than a new contract.

Default viewport refinement: explicitly set the chart's initial logical range to the latest 60 returned sessions. This keeps the daily candles readable while preserving the full 150-session history for user zoom and pan.

Weekly-flow refinement: replace the ambiguous 20D location/rising cue with four weekly low/high bars. Use W−3, W−2, and W−1 as completed weeks and WTD as the current week; mark adjacent lows within 1% as floor-aligned and expose higher/lower low and high sequences as compact HL/HH or LL/LH labels.

Delivery refinement: show today's delivery beside a prior-10-session average and current-to-average multiple. Label the recent delivery series Stable or Erratic using a transparent coefficient-of-variation threshold of 30%, with today excluded from the variability calculation.

Utility refinement: carry the familiar three-field buy/sell/change calculator into the compact route as a small right-aligned strip above the console, so it never covers the chart, tape, or notes. Preserve the existing behavior where any two values populate the third.

Weekly matrix refinement: show one row per week and one column per question—Week %, Low, High, Volume, Delivery, and Day %. Low/high dates stay inside the price columns; volume, delivery, and day movement use paired `L`/`H` values so each metric can be scanned vertically without reading prose.

Weekly matrix feedback refinement: tighten horizontal spacing and explain both baseline ratios in-place. A ratio below 0.8× is labelled `quiet`, 0.8–1.2× `near avg`, and above 1.2× `active`; Delivery shows its raw percentage plus the same prior-10-session baseline multiple.

Weekly relationship refinement: compare the normalized low-day and high-day values directly inside Volume and Delivery. Show `L > H · low-side`, `H > L · high-side`, or `L ≈ H · balanced`; this describes where participation concentrated without declaring accumulation or distribution.

Raw-volume refinement: remove the visible baseline adjectives from the weekly row and show raw share volume beside the normalized multiple, for example `L 23.35 L · 2.04×`. Keep the quiet/average/active thresholds discoverable through the cell tooltip rather than adding another reading step.

Latest simplification: remove the visible low-versus-high relationship cue from both Volume and Delivery. Keep Volume as raw shares plus its 10D multiple; keep Delivery as low/high percentages only.

Weekly movement refinement: keep the first-open-to-last-close Week % and add the low-to-high range percentage beneath it. Color the range line by sequence—green `L→H` when the low came first, red `H→L` when the high came first.

Width refinement: keep the weekly matrix at roughly 70% of the console width for a focused reading block; let the recent tape below retain the full console width.

Recent-tape refinement: include Open and Close in the compact daily rows, with one button below to expand from five sessions to the latest 30 sessions of full OHLC data.

Latest tape refinement: default to 10 sessions, combine Date/day, show Day % alongside the intraday `L→H %`, combine raw Volume with its 10D comparison, and keep Delivery as a single percentage. Remove the ambiguous `Spread` label.

Tape context refinement: tint and label the daily rows that created each visible weekly low or weekly high, preserving the same markers in the expanded 30-session view.

Tape spacing refinement: size the daily table to its content rather than stretching it across the full panel, keeping the columns grouped for faster scanning.

OHLC refinement: include Open, High, Low, and Close in the default 10-session tape; the 30-session toggle now changes row count only, not the candle columns.

Lower-layout refinement: place Four-week structure beside Recent tape on desktop, using a balanced 46% / 54% split, and stack the panels on narrower screens.

Volume-event refinement: place a four-row table below Four-week structure that ranks the four largest raw-volume sessions in the latest 40 days, with compact OHLC, Day %, and Delivery context for chart investigation.

Observation-log refinement: remove the repeated five-day narrative block from the compact story panel. Keep only a small Take note composer and the existing notes; each note shows when it was taken plus the matching session's OHLC, day move, raw volume/10D comparison, and delivery evidence.
