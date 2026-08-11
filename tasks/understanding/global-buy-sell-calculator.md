# Global buy/sell calculator

The buy/sell/change calculator is a shared utility, not part of any stock-review page. It should live in the application header so it is available on every console route and remains mounted while the user switches tabs. The compact review and legacy three-week review should not render their own copies, avoiding duplicate controls and floating overlays.

Implementation will keep the existing three-field calculation behavior and move only the component placement/styling into the app shell. Validation will cover the calculator behavior and confirm the page-specific copies are removed while the production build remains healthy.

Implemented: `BuySellChangeCalculator` now renders once in `App`'s header, while the compact and legacy review pages no longer render local or floating copies. Live route switching confirmed the global control remains present and preserves its entered value.

The compact stock identity also exposes a small stock icon linking to the selected NSE instrument's Kite chart in a new tab.

The header calculator is pinned to the viewport's top-right with a compact surface, so it remains visible during page scrolling.

The compact review header now also includes a small Move column for 20D, 40D, and 60D price movement from the matching prior trading-session close.

The Move column sits in a dedicated secondary header row, keeping the main evidence row from stretching and leaving that row available for future compact metrics.
