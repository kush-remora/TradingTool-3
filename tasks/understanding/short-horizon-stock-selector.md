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

The first frontend slice is now available as **5-Day Stock Selector**. It reuses the existing watchlist daily-history endpoint and adds a sidebar route at `console/short-horizon-selector`. The table shows the latest close, historical `+5% in 5D` count/rate, a low-to-high close-position marker with direction, and `Details` / `Review` actions. Details opens the successful starting days with their starting close, next-five-session high, move, and close position.

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
