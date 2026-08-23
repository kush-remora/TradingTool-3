# Task Understanding: Backtest Quality Gate & Climax Spread Integration

> Status: proposal only. The current backtest records quality context but does not reject entries by this proposal.

## Problem Statement
The user realized that relying purely on relative volume/delivery leads to entering "Buying Climaxes" (e.g., LENSKART on 27 Feb 2026). A stock can have a massive 1.7x volume spike with only 30% absolute delivery and a >7% intraday spread. This is the footprint of exhaustion/distribution, not quiet institutional accumulation.

Previously, the 6-Month Backtest Engine simulated trades on every fresh breakout. The implemented change now runs `BreakoutDayQualityAnalyzer` for every historical signal and records its result on the trade, but it deliberately keeps the result context-only. A future quality-gate experiment may use the proposal below to filter entries.

## Scope of Changes

### Proposed rule additions (`BreakoutDayQualityAnalyzer.kt`)
*   **Absolute Delivery % Rule**: Modify the delivery rule to compute absolute delivery percentage `(delivered / volume * 100)`. If `< 40%`, reject unconditionally (too much intraday churn). If `≥ 40%`, evaluate the 20-day ratio as usual.
*   **Climax Spread Rule**: Add a volatility rule `((High - Low) / Low * 100)`.
    *   PASS: `≤ 5.0%`
    *   WAIT: `5.01% - 6.5%`
    *   REJECT: `> 6.5%` (Distribution/exhaustion risk)

### Proposed backtest model changes (`AdaptiveBreakoutBacktestModels.kt`)
*   Add `REJECTED_BY_QUALITY` to `AdaptiveBreakoutBacktestExitReason`.
*   Make `entryDate`, `entryPrice`, `targetPrice`, `stopPrice`, `exitDate`, `exitPrice`, `holdingSessions`, `returnPct` nullable in `AdaptiveBreakoutBacktestTrade`.
*   Add `rejectReasonSummary: String?` to `AdaptiveBreakoutBacktestTrade` to store why the quality gate failed.

### Proposed backtest engine changes (`AdaptiveBreakoutBacktestEngine.kt`)
*   Update `run()` signature to accept `deliveriesByToken: Map<Long, List<StockDeliveryDaily>>`.
*   Inside the breakout loop, extract the history up to the breakout date and call `BreakoutDayQualityAnalyzer.analyze()`.
*   If `overallDecision == REJECT`, add a trade record with `exitReason = REJECTED_BY_QUALITY` and the rejection summary. Skip the `simulateTrade` entry logic.

### Proposed service changes (`AdaptiveBreakoutScannerService.kt`)
*   In `runWatchlistBacktest` and `runBacktest`, fetch delivery data for all involved tokens covering the `testFromDate.minusMonths(6)` to `testToDate` period.
*   Group by token and pass into the `AdaptiveBreakoutBacktestEngine`.

### Proposed frontend changes (`AdaptiveBreakoutBacktestPage.tsx`)
*   Update columns to handle `REJECTED_BY_QUALITY`.
*   If rejected, display the `rejectReasonSummary` in the Result column with a gray/warning tag.
*   Show empty states (`—`) for Entry, Target, Stop, and Exit columns.

## Current implementation

- Four context checks are scored: buyer days, close-position trend, volume dry-up, and breakout-day close position.
- Delivery is shown as a 20-session quantity/percentage context block and is not scored.
- `PASS`, `WAIT`, and `REJECT` are attached to each trade for later comparison; they do not filter entries.
- Entry triggers are selectable between `FRESH_BREAKOUT` and `CEILING_RECLAIM`.
- Entries with a first later tradable open more than the configured gap threshold above the breakout close are recorded as `SKIPPED_ENTRY_GAP`.
- Exit fills use the session open for gap-through target/stop moves, and reported returns are net of configurable round-trip transaction costs and slippage. The summary includes expectancy, average winning/losing return, payoff ratio, and profit factor.
- Active ceilings expire after the configured maximum age, and failed-attempt caps stop ratcheting after the configured number of attempts. This preserves the stable base ceiling while preventing stale moving goalposts.
- Watchlist results use the current constituent list; historical constituent membership is not reconstructed, so survivorship bias remains a documented limitation.
- Quality-based entry filtering is intentionally deferred. All selected signals remain in the replay and receive quality context.

Phase 1 anomaly scanning owns cap-adaptive delivery thresholds: MID 55%, SMALL 70%, MICRO 85%, and NANO 92%. Breakout Day Quality does not score delivery; it reports stock-relative delivery context separately to avoid conflating the two workflows.

## Technical Considerations
*   **Performance**: Fetching 6 months of delivery data for a 100-stock watchlist might be heavy. `StockDeliveryReadDao` can handle large `IN (...)` queries, but we should batch it if necessary.
*   **Proposed decision logic**: `BreakoutDayQualityAnalyzer` returns `WAIT`, `PASS`, or `REJECT`. If this proposal is implemented, choose explicitly whether only `PASS`, or both `PASS` and `WAIT`, are allowed to enter; the current implementation does not use `REJECT` to skip entry.

## Phasing Update
Implementing this proposal remains a separate experiment. Do not describe the current backtest as quality-gated until a configurable entry filter and dedicated outcome comparison are added.
