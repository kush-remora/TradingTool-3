# Accumulation Scanner V1 — Understanding

## Current understanding — 2026-08-14

Build a watchlist-based accumulation scanner that presents a ranked or sortable stock table. For each stock, show recent evidence of buying interest, positive days, and quiet/low-movement days, with a compact daily heatmap so the user can see whether the behavior is isolated or persistent. The first version is intended as an observation tool for possible Wyckoff accumulation, not as a buy signal.

Working window: calculate counts over the latest 30 trading sessions and show a compact heatmap over the latest 20 sessions. The accepted metrics are: (1) buying-interest days where `(close - low) / (high - low) >= 0.70`; (2) green days where `close > previous close`; and (3) quiet days where the absolute close-to-close change is `< 1%`. Add a fourth volume view: compare each day’s volume with its prior 10-session average, count days below that average, and show the corresponding heatmap. The table should make the four counts easy to compare and preserve the daily sequence behind each count.

## Integration decision

Build this as an **Accumulation** super-tab inside the existing **5-Day Stock Selector**. This keeps the short-trade selection and accumulation context in one watchlist workflow; a separate console is unnecessary for V1. Keep the counts independently sortable and do not create a combined score.

Volume dry-up uses the prior 10 completed trading sessions as the baseline, excluding the day being classified. If fewer than 10 prior sessions exist, that day has no volume classification and is excluded from the volume count.

## Initial product boundary

In scope: the existing watchlist selector, one Accumulation super-tab, stock rows, independently sortable 30-session counts, latest-20-session heatmaps for price and volume, trading-day handling, and clear metric definitions. Out of scope for V1: delivery-volume rules, support/base validation, Wyckoff event labels, buy signals, backtesting, and automatic trade recommendations.

## Implementation outcome

Implemented in the 5-Day Stock Selector. The tab defaults to buy-interest sorting, keeps all four counts independently sortable, and renders Buy/Green/Quiet/Vol dots from oldest to latest. The volume count uses only sessions with a completed prior-10-session baseline. Focused utility/page tests and the production frontend build passed; the broader frontend suite retained unrelated timeout/stale assertion failures.
