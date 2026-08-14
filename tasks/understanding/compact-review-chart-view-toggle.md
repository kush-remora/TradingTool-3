# Compact Review Chart View Toggle

The compact stock review currently renders the price pane with a Lightweight Charts candlestick series and keeps the volume histogram, volume-signal toggles, event markers, DMA guides, hover readout, and recent-session range behavior in the same chart component. The requested change is a small presentation control for the price pane: let the user switch between an OHLC bar view and a close-price line view without changing the loaded data or review evidence.

Implementation keeps Bar as the default view for the compact, evidence-first workflow and exposes an accessible Bar/Line toggle beside the existing signal controls. Regression coverage verifies the active series data and that switching views recreates the chart with the appropriate series while retaining the readout behavior.

Implemented with Lightweight Charts `BarSeries` and `LineSeries`; the volume histogram, markers, DMA guides, readout, and opening range remain shared. Focused chart tests (5) and the frontend production build pass. The broader compact-review suite has two unrelated pre-existing failures in date/header assertions; the other 12 tests pass.
