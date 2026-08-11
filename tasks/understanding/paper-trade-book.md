# Paper Trade Book

The user needs a separate, low-friction paper-trading screen for validating daily chart reads without risking real capital. A new position should require only a stock and entry price; quantity is always one share, the entry date is today, and the existing backend receives a hidden 5% default stop-loss value.

The screen now prioritizes open positions: entry date, entry price, live current price, current P&L amount/percentage, and days held. Close/remove actions remain available but visually secondary. Closed trades live in a collapsed history section so the conviction loop stays focused on active ideas. Existing trade endpoints and persistence are reused; no backend contract or database change is needed.

The compact stock review now exposes the same flow contextually: its Paper trade action opens the quick form for the current symbol with the latest available price prefilled. It submits directly to the existing create-trade endpoint, keeping the review page lightweight.

When a matching open paper trade already exists, the compact review shows a small stacked position block beside Last fresh breakout with entry date, entry price, current P&L percentage/amount, and holding days. The paper-trade action sits beside Take note in the Observation log. Closed trades remain absent from this block.
