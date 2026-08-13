# Short-horizon stock selector

## Current understanding

This is a small watchlist-level attention selector that sits before the existing Console / Compact Stock Review flow. Its job is to reduce a watchlist to a few stocks worth opening in the detailed review. The intended trade is short: enter and exit within one to five trading days, with a 5% to 10% upside objective. It is a research and prioritisation tool, not a buy/sell recommendation engine.

The first version should focus on the one-to-five-day swing horizon using daily data. True intraday selection is a separate problem because it needs intraday candles, session timing, and a different entry/exit model. We should not combine both horizons in the first screen.

## Product idea

The selector should answer one question:

> Which stocks in this watchlist have recently demonstrated enough short-horizon movement to make a 5% to 10% objective plausible, and which of them deserve a Compact Stock Review now?

The workflow is: choose one watchlist → see a compact, sortable list → select one stock → open the existing Compact Stock Review for evidence and judgement.

The selector should not pretend to predict a target. It should progressively add evidence about three separate questions:

1. Can this stock move far enough within five sessions?
2. Is there a fresh directional setup now?
3. Can it be traded cleanly enough for the intended holding period?

## Proposed first data point

Start with **historical 5-session +5% reach rate**. For each completed historical session, treat that session's close as a reference and check whether the following five trading sessions' high reached at least 5% above it. Show the percentage of qualifying reference sessions over a fixed recent window, along with the sample count.

Reason for adding it: the strategy's minimum objective is 5% within a maximum five-session hold. This data point tests the stock's demonstrated ability to satisfy the basic payoff/holding-period requirement before we add setup narratives, indicators, or scoring. It must use only prior completed history for the current selector row; no future candle may leak into today's decision.

Do not add 10% reach rate, ATR, RSI, volume, delivery, or a composite score until the first metric is reviewed and accepted. Each later field should answer a distinct question and have a written reason for inclusion.

## Initial product boundary

The first selector begins with historical capacity plus the latest close position. A separate current-setup metric, rejection/give-back measure, or intraday model remains outside v1 until the first screen has been reviewed.

## One-glance UI direction

The main screen should feel like a short list, not an analytics dashboard. Keep only two primary signals visible for each stock:

- **5D target history:** a compact value such as `8/20 reached +5%`.
- **Latest close strength:** a small low-to-high line with a dot showing where the close finished, labelled `HIGH`, `MID`, or `LOW` and paired with an up, sideways, or down direction.

Use green only for a strong close, red only for a weak close, and neutral grey for the middle. Keep the actual numbers visible so colour is not the only meaning. The only row action should be `Review`, which opens Compact Stock Review.

Do not show an overall score, buy label, ten separate columns, or a detailed rejection/give-back calculation on the first screen. If give-back behaviour is later added, it should be a small secondary field or appear in the detailed review after its definition is agreed.

## Success-day detail popup

The `8/20 reached +5%` history should have a small `Details` button. The main row remains compact; the popup explains where the close was on the days that later succeeded.

Example popup content:

- `8 successful days out of 20`
- `5 closed near the day's high`
- `2 closed in the middle`
- `1 closed near the low`
- A compact list of those dates, showing the starting close, the next-five-day highest price, the resulting move, and the starting day's close position.

This creates the useful connection between **the stock eventually reaching the target** and **how strong the starting day looked**. It is evidence for review, not a new score or an automatic recommendation.

## Implemented v1

The first frontend slice is now available as **5-Day Stock Selector**. It reuses the existing watchlist daily-history endpoint and adds a sidebar route at `console/short-horizon-selector`. The table shows the latest close, historical `+5% in 5D` count/rate, a low-to-high close-position marker with direction, and `Details` / `Review` actions. Details opens the latest 20 completed sessions with OHLC, close-to-close change, close position within the daily range, and distance from the day's high.

The page now has three tabs: **All Stocks**, which keeps the full watchlist visible; **Shortlist**, which first applies minimum guards and then combines two adaptive rankings; and **Core**, which keeps only the overlap of those rankings. A stock must have at least 5 successful days out of 20, at least 2 successful days out of the most recent 6 eligible starting days, and its latest close must be no more than 10% below the recent 20-session high. It is also removed when the last 3 closes fall in a row and the latest close breaks below the previous 5-session low; three red candles alone are not enough. The original watchlist size sets the limit for each ranking: `ceil(20% of the original watchlist)`, capped at 20 stocks. The guards only remove weak or clearly late stocks; they do not shrink that limit. One ranking uses the full 20-day successful-day count, and the other uses the most recent 6 eligible starting days. The union is therefore never larger than 40 stocks, and the screen never fills the quota with weak or clearly late stocks. Tab 1 shows both counts so the reason for a shortlist entry is visible. The Shortlist tab also shows the recent high and volume evidence. These are shown as context only; no distribution conclusion is applied.

The third **Core** tab keeps only the intersection: a stock must appear in both ranking groups. It uses the same guards and the same original-watchlist-based ranking limit, so it is a smaller, higher-agreement review list rather than a new score. Core rows are ordered by distance from the 52-week high, closest first. That distance is visible context and does not reject a stock.

The Core tab now also shows the current move over 5 sessions and 20 sessions, calculated from the latest close versus the close that many trading sessions earlier. These two values answer whether the historical candidate is still moving now. They are context only and do not change Core membership.

The calculation uses only completed five-session windows. It reports the actual usable reference-day count, so sparse history is visible rather than silently presented as a full 20-day sample. No backend contract, score, buy label, rejection metric, or intraday logic was added.

Validation: the focused selector tests passed and the production frontend build passed. The full frontend suite completed with 50 passing test files / 149 passing tests and 10 existing failing test files / 17 failures, mainly default-timeout and stale Compact Stock Review assertions outside this feature. The new selector page and utility tests passed during that run.

Next review point: inspect the visual density and decide whether the current close-position label should remain `HIGH / MID / LOW`, and whether the historical window should stay at 20 usable starting days.

## Implemented current-weakness guard

Liquidity is not the next priority for a two-block retail position. The next concern is whether a historically successful move has already become late and weak. Two related observations are being considered:

1. **Pullback from the recent move high:** show how far the latest close has fallen from the highest high of the recent move. This directly catches the `₹150 → ₹140` example and is the stronger first filter because it answers whether the stock is still near its actionable area.
2. **Heavy down-volume day:** count recent sessions where price declined while volume was unusually large compared with its recent normal volume. This is a warning that supply may be appearing, but it must not be labelled confirmed distribution from one bar alone.

The selector now rejects a stock only when three consecutive closing prices are falling and the latest close breaks below the previous five-session floor. A three-red-candle sequence by itself remains valid because it can represent a controlled pause. Heavy down-volume remains context only and is not an automatic distribution verdict.

## Review consolidation

A comprehensive second-pass review is captured at `docs/features/short-horizon-stock-selector/reviews/2026-08-13-short-term-trading-console-review.md`. The key decision is that the selector is useful as a Core-list generator but should not become a buy signal. The next highest-value improvement is a simple **move quality** field that separates clean upward movement from wild up/down movement. Volume should become an **exit pressure** warning, not a confirmed distribution filter. Risk floor should remain context only because clean bull-run movers can appear far from their recent floor without being bad candidates.

## Review validation status (non-final)

The review's current-state observations match the implementation: historical `+5%` means future price-range reach, not a captured trade; the recent six are six eligible tested starting days; Core is the overlap of two rankings; the `-10%` recent-high guard and three-declining-closes-plus-floor-break guard are active; volume is descriptive evidence; and 52-week-high distance only orders Core. Focused selector validation passed with 14 tests.

The following decisions are intentionally still open: accept the **move quality** question but define and test its thresholds before adding labels; accept **exit pressure** as a warning concept but do not infer distribution or rename the current volume field until its context rule is explicit; keep risk-floor information out of hard filtering; and simplify Core only after the new visible signals have been defined. The `6D` label has been renamed to `Recent tested 6D`; RSI/composite scores/buy labels remain excluded; and the selector-to-Compact-Review boundary is preserved.

## Open question: reach rate versus held-trade return

The current `20D 15/20` and `Recent tested 6D` values must not be interpreted as “the stock moved 5% on 15 of 20 days” or “buying on those days would have produced fifteen 5% trades.” Each count asks a narrower question: from each eligible starting close, did the highest price in the next five trading sessions touch at least 5% above that close? The six-day value is six recent tested starting days, not a six-day return.

This is still useful for the selector because it measures whether the stock repeatedly offers enough short-horizon upside range. It is not an executable return or expectancy measure because the price may touch +5% briefly, may fall first, and the five-session windows can overlap. The visible wording should therefore say `5D reach: 15/20` or `Touched +5% within next 5 sessions`, never imply that a trade would have captured the move.

Before treating this as stronger evidence, validate complementary path measures such as time to target, maximum drawdown before target, close-to-close five-session result, and possibly non-overlapping opportunity episodes. Keep the reach rate as the first-stage capacity filter unless those checks show that it is misleading for the intended trade.

## Implemented strong-finish context

Core now shows `Strong finishes` as a transparent current-pressure metric. It counts the latest five completed candles where the close finished above 60% of the candle's high–low range. The denominator uses the available recent sessions, and the metric is context only: it does not filter, rank, or imply a trend reversal.

## Implemented first move-quality rule

Core now also shows `Move quality` for the latest five completed candles. `Clean` requires positive net movement, at least 3 rising closes, at least 3 strong finishes, no more than 1 direction change, and path efficiency of at least 60%. `Wild` means at least 3 direction changes or path efficiency below 35%; all other complete five-candle windows are `Mixed`. The label is context only and does not filter or rank stocks.

## Implemented recent daily details

The Details modal now prioritizes the latest 20 completed sessions, newest first. It shows open, high, low, close, close-to-close change, close position within the day's range, and close distance from that day's high. The first displayed change uses the preceding session outside the 20-row window when available; no future data is used.

The read-only Details modal is modeless and has no blocking mask, so the global buy/sell calculator can be used while the recent daily evidence remains open.

All Stocks now shows a sortable `Strong finishes` column with the count out of five and five filled/empty dots in newest-first order. Filled dots mean the close finished in the upper 40% of the day's high–low range; hover still exposes the date and sequence position, and the metric does not affect selection.

All Stocks now shows a sortable compact `Move now` column with non-overlapping `Now 5D`, `Prior 5D`, and `Earlier 10D` close-to-close movement buckets. The cell uses green/red direction, a pace arrow comparing Now 5D with Prior 5D using a 1 percentage-point tolerance, and an amber extension watch when the hidden 20D total reaches +25% or more. These values provide current activity context and do not affect selection.

## Implemented exit-pressure warning

Shortlist and Core now show a sortable `Exit pressure` sequence warning using the latest three completed sessions and a preceding 10-session volume baseline. A meaningful push requires volume of at least `1.5×`, a close above 60% of the range, a positive close versus the prior session, and a positive 20D move. `Quiet` means no relevant pattern. `Watch` means a strong push or strong close is not yet confirmed by the next session, or the next session shows weak follow-through without enough volume confirmation. `Caution` means the meaningful push is followed by a close below the push-day close with a weak finish, at least a 1% decline, or a break below the push low. This is a possible supply sequence, not confirmed distribution, and it does not filter, rank, or change membership.

Validation for this addition: the focused selector suite passed with 20 tests, and the production frontend build passed. The page test confirms that the sortable column appears once in Shortlist and Core.

## Tab 1 current-signal priority

Tab 1 now treats current condition as the primary reading order: Move now, Strong finishes beside Latest finish, Move quality, and Exit pressure. Latest close remains supporting context, while 5D reach and Recent tested 6D remain historical context rather than the lead signal. This is a presentation change only; Tab 2 and Core membership rules are unchanged.

The recent-details popup also shows each completed session's volume as a multiple of the preceding ten-session average, without displaying raw volume. This uses the same short-horizon baseline as Exit pressure and helps inspect volume action alongside OHLC, close position, and change.

## Implemented Tab 1 learning guide

The Tab 1 header now has a `How to read Tab 1` button. It loads a backend-owned JSON guide through `/api/strategy/short-horizon-selector/tab-one-guide` and opens a reusable table with each column's meaning, importance, reading method, and caution. The source file is `core/src/main/resources/short_horizon_selector/tab_one_guide.json`, so wording can be updated without changing scanner calculations. The guide is fetched without the frontend cache whenever it is opened.

## Implemented compact move reading

The All Stocks move column now shows `Now 5D`, `Prior 5D`, and `Earlier 10D` one below the other. The repeated period labels are intentionally small and light; the percentage values carry the visual emphasis. Positive values are green and negative values are red. The arrow compares Now 5D with Prior 5D, so it directly communicates recent pace without overlapping-window mental math. The hidden 20D total still controls the amber extension watch and remains available in hover text.

`Strong finishes` and `Latest finish` are adjacent on Tab 1. Strong finishes shows repeated buyer control across five sessions; Latest finish shows the most recent candle's close position. Latest finish is kept compact so the two demand signals can be read together.

The All Stocks `Latest close` cell now also shows the total `20D` movement and distance from the recent 20-session high, for example `20D +7.1% · -3.2% from high`. An amber caution symbol appears only when the 20D move is strictly above 25%; `+25.0%` does not trigger it. This is an extension warning, not an automatic rejection or buy rule.
