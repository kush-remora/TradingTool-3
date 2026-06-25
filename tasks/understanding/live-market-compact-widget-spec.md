# Live market compact widget spec

The right direction is a 2-layer system instead of one overloaded widget. We should keep a compact universal live-market cell for all screener tables, and separately build a richer intraday review panel/screen later. The compact cell is for scanning many stocks quickly. The intraday panel is for deeper decision support on a smaller set of names. We should not force the table cell to do both jobs because that will reduce scan speed and make the table visually noisy.

The compact widget should become the shared plugin-play component used across tables wherever we currently show a `Live Market` column. Its job is to answer only the highest-value questions: what is price doing now, is price above or below average price, is today's volume materially different from `T-1`, and is buyer/seller pressure supportive. The field hierarchy should remain: `LTP`, `% change`, `LTP vs average_price`, and `today volume / T-1 volume`, with buy/sell quantity treated as optional secondary context.

## Widget modes

We should define two compact modes backed by the same data contract:

- `standard`: for most tables, target width around `140px` to `160px`
- `wide`: for tables with more room, target width around `180px` to `210px`

Both modes should use the same underlying live data and interpretation logic so the behavior stays consistent everywhere.

## Standard compact layout

This should be the default reusable cell.

Line 1:
- `LTP`
- `% change`

Line 2:
- `AvgPx` relation
- Example: `Above Avg +0.8%` or `Below Avg -0.6%`

Line 3:
- `Vol vs T-1`
- Example: `Vol 1.9x`

Line 4:
- optional micro pressure badge
- `B`, `S`, or `N`

This version should be optimized for fast table scanning and kept dense.

## Wide compact layout

This version should be used only where the table has enough room.

Line 1:
- `LTP`
- `% change`

Line 2:
- `O H L`

Line 3:
- `AvgPx`
- `Delta vs AvgPx`

Line 4:
- `Today Vol`
- `T-1 Vol`

Line 5:
- `Vol Ratio vs T-1`
- pressure badge

Even this wide version should still feel like a compact table cell, not an intraday dashboard.

## Required data contract

The shared component should eventually read these fields:

- `ltp`
- `changePercent`
- `averagePrice`
- `volume`
- `previousDayVolume`
- `buyQuantity`
- `sellQuantity`
- `open`
- `high`
- `low`

Derived values to compute in the component layer:

- `ltpVsAveragePricePercent`
- `volumeRatioVsPreviousDay`
- optional pressure state from buy vs sell quantity

## Interpretation rules

The compact widget should preserve this signal hierarchy:

1. `LTP`
2. `% change`
3. `LTP vs average_price`
4. `today volume vs T-1 volume`
5. buy/sell quantity as supporting context only

Interpretation guidance:

- if `LTP > average_price`, intraday tone is stronger
- if `LTP < average_price`, intraday tone is weaker
- if `today volume / T-1 volume` is materially above `1.0x`, participation is stronger than yesterday
- if buy quantity is clearly above sell quantity, it can support bullish tone, but it must stay secondary

Suggested volume context bands:

- `>= 2.0x`: strong
- `1.3x` to `< 2.0x`: building
- `< 1.3x`: normal or quiet

Suggested pressure states:

- `B`: buyer-heavy
- `S`: seller-heavy
- `N`: neutral/mixed

Pressure should be low-emphasis visually compared with price and volume ratio.

## What not to do

- do not put every raw live field into every table cell
- do not make buy/sell quantity the main decision field
- do not rely on long labels in compact mode
- do not allow the compact cell to grow into a mini dashboard

## Component plan

We should eventually build:

- `LiveMarketCompactCell`
  - supports `mode="standard"` and `mode="wide"`
  - shared across all table screens

Later:

- `LiveMarketIntradayPanel`
  - separate richer component for deep live review

This keeps the plugin-play architecture clean: one shared compact cell for every table, and one separate deeper view for focused intraday work.

## Rollout plan

1. Confirm backend/live parser can expose `averagePrice` from Kite full mode.
2. Extend the shared backend and frontend live-market contract with `averagePrice`.
3. Build `LiveMarketCompactCell` with `standard` and `wide` modes.
4. Replace current per-screen ad hoc live-market rendering with this shared component.
5. Build the dedicated intraday panel later using the same data contract.

## Implementation note on 2026-06-25

The first implementation pass should start with the shared data contract rather than the visual layer. Local SDK inspection confirmed:

- WebSocket tick model: `com.zerodhatech.models.Tick#getAverageTradePrice()`
- Quote snapshot model: `com.zerodhatech.models.Quote#averagePrice`

That means the app can expose `averagePrice` from both live SSE updates and snapshot quote fetches without inventing a derived proxy. The clean rollout is:

- add `averagePrice` to `TickSnapshot`
- map Kite tick `averageTradePrice` inside `KiteTickerService`
- add `averagePrice` to `LiveMarketUpdate`
- add `average_price` to `StockQuoteSnapshot`
- update the frontend shared types and widget to consume that field

The first production visual version should still stay minimal:

- `LTP`
- `%`
- `Above Avg` / `Below Avg`
- `Vol x T-1`

This keeps the first rollout useful and plugin-safe across many tables without waiting for the later intraday panel.

## First production version recommendation

The first production version should stay minimal and high-signal. The default `standard` compact mode should show:

- `LTP`
- `%`
- `Above Avg` / `Below Avg`
- `Vol x T-1`

That gives the biggest immediate upgrade in decision quality without bloating tables.

## Pressure bar follow-up

The better compact evolution is not `B / S / N`, but a tiny split pressure bar using live `buyQuantity` and `sellQuantity`. This should remain explicitly secondary and low-emphasis, but it should preserve balance strength instead of collapsing everything into one letter.

Recommended rendering:

- `standard` mode:
  - tiny green/red split bar beside the volume-ratio line
  - no extra words unless space clearly allows it
- `wide` mode:
  - split bar plus `Buy xx%` and `Sell xx%`

Recommended rule:

- compute `buyPct = buyQuantity / (buyQuantity + sellQuantity)`
- compute `sellPct = sellQuantity / (buyQuantity + sellQuantity)`
- if either side is missing, fall back to a neutral-looking equal split

This keeps pressure readable like Groww while still keeping it below price, `Avg`, and volume context in the signal hierarchy.

## Initial mode rollout on 2026-06-25

The first explicit `wide` mode rollout should stay selective.

- Use `wide` in `Phase D` because that table is already a narrower conviction review surface where extra live context improves validation quality.
- Use `wide` in `Trade Book` because positions are few and each row is decision-sensitive, so richer live context is worth the width.
- Keep broad scanners such as Phase 1, Delivery Breakout, Hot SMA, and Volume Shocker on `standard` mode for now because those screens optimize for cross-row scan speed and density.

This keeps the component plugin-play across all screens while letting the high-conviction tables surface deeper context first.
