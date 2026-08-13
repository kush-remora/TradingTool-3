# Short-Term Trading Console Review

Date: 2026-08-13

## Goal

Build a watchlist selector that helps pick stocks for a short trade:

- Profit target: 5% to 10%.
- Holding period: 1 to 5 trading days.
- User style: retail position size, around one or two blocks, so liquidity is not the first concern.
- Main need: reduce 100 stocks into a small review list without creating false confidence.

This console should not say "buy". It should say:

> This stock has the right short-term movement character. Review it now.

## Final Review Opinion

The console is useful as a first-stage selector. I would use it to reduce a watchlist into a Core list.

I would not use it as a direct trade-entry console yet. The missing piece is not another generic indicator. The missing piece is a clean answer to:

> Is the stock moving cleanly now, or is it just volatile/noisy?

The next improvement should focus on move quality:

- Prefer a controlled upward move.
- Be cautious with wild up/down movement.
- Treat large recent volume as a possible crowd-exit warning.
- Treat risk floor as context, not as an automatic rejection rule in a bull run.

## Current Console Understanding

The current console has three tabs:

1. All Stocks
2. Shortlist
3. Core

### All Stocks

Shows the full selected watchlist.

Purpose:

- Keep visibility of all symbols.
- Show basic movement evidence.
- Do not use this as a trading list.

Interpretation:

- Valid use: "Which stocks have any short-term movement history?"
- Invalid use: "The top stock in All is a buy."

### Shortlist

Creates a smaller list using two selection ideas:

- Best by last 20 usable starting days.
- Best by recent 6 usable starting days.

It uses a union of both groups.

Purpose:

- Keep both stable movers and newly active movers.

Interpretation:

- Valid use: "This stock deserves attention."
- Invalid use: "This stock is ready to buy today."

### Core

Keeps only stocks that appear in both rankings.

Purpose:

- Find stocks where the long-window evidence and recent evidence agree.

Interpretation:

- Valid use: "This is the main review list."
- Invalid use: "Everything in Core must be traded."

## Current Rules

### Historical Target Rule

For each stock, the console checks whether the stock reached at least +5% within the next 5 trading sessions from a starting close.

Example:

- Day 1 close: Rs. 100
- Highest price during next 5 sessions: Rs. 106
- Move: +6%
- Result: successful starting day

Invalid interpretation:

- "I would definitely have booked +6%."

Correct interpretation:

- "The stock had enough movement to touch the target."

Why this matters:

The high may have happened briefly. The console does not prove that entry, exit, slippage, and psychology would have captured the move.

### 20-Day Count

The console counts how many of the last 20 usable starting days reached +5% within the next 5 sessions.

Example:

- `8 / 20` means 8 starting days touched +5% within 5 sessions.

Good:

- It shows whether this stock has short-term movement habit.

Bad:

- It can favor stocks that were active earlier but are no longer active now.

### Recent 6 Count

The console counts how many of the latest 6 usable starting days reached +5% within the next 5 sessions.

Important detail:

This is not exactly the latest 6 calendar/trading days. Because the rule needs 5 future sessions to know the outcome, these 6 starting days are already at least 5 sessions behind the latest candle.

Recommended naming:

- Current label: `6D`
- Better label: `Recent 6 tested days`

Valid example:

- `20D 6/20`
- `Recent tested 6D 4/6`
- Meaning: movement habit is still recent.

Invalid example:

- `20D 10/20`
- `Recent tested 6D 0/6`
- Meaning: it was moving earlier, but recent movement may have cooled.

## What Is Good

### 1. The Base Idea Matches The Goal

The goal is 5% to 10% profit in maximum 5 days. Starting with stocks that have already touched +5% within 5 days is correct.

Good example:

- Stock A touched +5% in 9 of the last 20 tested days.
- Stock B touched +5% in 1 of the last 20 tested days.
- Stock A deserves review before Stock B.

### 2. The Two-Window Selection Is Useful

Using both 20-day and recent 6-day evidence avoids one common mistake:

- Only 20-day count can select stale movers.
- Only recent count can select one-week noise.

Good example:

- Stock A: `20D 8/20`, `Recent 6D 4/6`
- Interpretation: active historically and active recently.

Weak example:

- Stock B: `20D 10/20`, `Recent 6D 1/6`
- Interpretation: was active, but maybe cooling.

### 3. Core Tab Is The Right Main Work Area

The Core tab keeps stocks selected by both rankings.

This is good because it reduces decision fatigue. If the watchlist has 100 stocks and Core has 10 to 15, the user can review only serious candidates.

Recommended behavior:

- All Stocks: audit only.
- Shortlist: attention only.
- Core: actual review list.

### 4. Recent High Pullback Is Useful

The current guard rejects stocks more than 10% below the recent 20-session high.

This catches the situation:

- Stock ran from Rs. 100 to Rs. 150.
- Now it is at Rs. 135.
- It may still have old +5% history, but the current move may be damaged.

Valid rejection:

- Recent high: Rs. 150
- Latest close: Rs. 132
- From high: -12%
- Interpretation: too much damage for a short 5-day entry.

Valid keep:

- Recent high: Rs. 150
- Latest close: Rs. 143
- From high: -4.7%
- Interpretation: still near the move area.

### 5. Three Falling Closes Alone Are Not Enough To Reject

The current logic rejects only when both are true:

- Last 3 closes are falling.
- Latest close breaks below the previous 5-session low.

This is good.

Valid keep:

- Closes: 150, 148, 146
- Previous 5-session low: 142
- Latest close: 146
- Interpretation: controlled pullback, not broken.

Valid reject:

- Closes: 150, 146, 139
- Previous 5-session low: 142
- Latest close: 139
- Interpretation: short-term floor broke.

### 6. Details Popup Is Correct

The main row stays compact, while details explain what happened on successful starting days.

Useful question answered:

> On days that later worked, did the stock close near the high or near the low?

Good example:

- Successful days: 8
- Close near high: 6
- Close middle: 2
- Close near low: 0
- Interpretation: successful moves often started from strong closes.

Weak example:

- Successful days: 8
- Close near high: 1
- Close middle: 2
- Close near low: 5
- Interpretation: historical success exists, but starting-day quality is not clean.

## What Is Bad Or Incomplete

### 1. The Console Can Create False Confidence

The console says a stock touched +5%. It does not prove the user would have captured +5%.

Bad interpretation:

- "10/20 means I will win 50% of trades."

Correct interpretation:

- "10/20 means price had enough future range 10 times."

Why it matters:

In a real trade, entry may happen late, target may touch briefly, or the stock may fall first before rising.

### 2. Move Quality Is Missing

This is the biggest missing selection signal.

There are two different kinds of movement:

Clean upward movement:

- Day 1: +1.2%
- Day 2: +1.8%
- Day 3: +0.9%
- Day 4: +2.1%
- Day 5: +1.4%
- Interpretation: controlled demand, easier to hold.

Wild movement:

- Day 1: +5%
- Day 2: -4%
- Day 3: +6%
- Day 4: -5%
- Day 5: +3%
- Interpretation: noisy, hard to enter and hard to hold.

For this console, clean movement is more useful than raw risk floor.

### 3. Risk Floor Is Useful But Not A Selection Filter

Earlier idea:

- Calculate how far price is from the recent floor.

This is useful, but in a bull run it can mislead.

Bull-run example:

- Stock moves from Rs. 100 to Rs. 125 in a clean trend.
- Recent floor is Rs. 108.
- Risk to floor looks like -13.6%.

Bad interpretation:

- "Risk is too high, reject it."

Correct interpretation:

- "The stock has moved away from support. Use this as caution, not automatic rejection."

Recommendation:

- Show risk floor in details or as context.
- Do not use it as a hard filter.

### 4. Volume Logic Needs Better Meaning

Current volume field:

- Largest volume in recent 5 sessions compared with prior 20-session average.

The user's better interpretation:

> If a stock has been running for many days, holders have profit. If a very large volume appears near the end of the run, it may mean some holders are exiting. Be cautious.

Correct use:

- Volume is a crowd-exit warning.

Incorrect use:

- Large volume means confirmed distribution.

Large green volume example:

- Stock ran from Rs. 100 to Rs. 140.
- Today volume is 4x normal.
- Today candle is green.
- Interpretation: still caution. Some buyers entered, but old holders may have sold into them.

Large red volume example:

- Stock ran from Rs. 100 to Rs. 140.
- Today volume is 4x normal.
- Today candle is red.
- Interpretation: stronger caution. Exit pressure may already be visible.

Quiet volume example:

- Stock ran from Rs. 100 to Rs. 130.
- Last 2 days volume is normal or below normal.
- Price holds near high.
- Interpretation: holders may still be holding; further run is possible.

Recommendation:

- Rename the field from `Largest 5D volume` to `Exit pressure?`
- Show labels:
  - `Quiet` - no unusual recent volume.
  - `Watch` - large volume appeared.
  - `Caution` - large volume plus weak close.

### 5. 52-Week High Can Make Us Chase

Being near a 52-week high is not automatically good or bad.

Good near-high example:

- 20D move: +8%
- 5D move: +2%
- Close near high
- Volume quiet
- Interpretation: controlled continuation.

Bad near-high example:

- 20D move: +35%
- 5D move: +14%
- Huge recent volume
- Close weak
- Interpretation: late and risky.

Recommendation:

- Keep 52-week high distance as a sort/context signal.
- Do not use it as a direct buy reason.

### 6. Core Table Has Too Many Equal-Weight Signals

Current Core columns include:

- Stock
- Latest close
- Target reached
- 5D move
- 20D move
- Recent high
- From high
- Largest 5D volume
- 52W high
- Latest finish
- Actions

Problem:

All columns look similarly important, so the user still has to read too much.

Recommended visual order:

1. Stock
2. 5D target habit
3. Now move
4. Move quality
5. Exit pressure
6. Latest finish
7. Review

Less important fields should move into details:

- Recent high price
- 52-week high price
- Exact volume date
- Historical successful-day list

## Recommended Signal System

Every signal should have exactly one role:

| Signal | Role | Why |
| --- | --- | --- |
| 20D +5% count | Filter + rank | Shows short-term movement habit |
| Recent tested 6D +5% count | Filter + rank | Shows recent movement habit |
| Core overlap | Filter | Requires both stable and recent evidence |
| Pullback from recent high | Filter | Removes clearly damaged stocks |
| Three falling closes plus floor break | Filter | Removes short-term breakdown |
| 5D move | Warning/sort | Shows whether entry may be late |
| 20D move | Context/sort | Shows broader current trend |
| 52W high distance | Sort/context | Helps prioritize near-breakout names |
| Volume spike | Warning | Possible crowd-exit pressure |
| Risk floor | Context only | Useful for review, weak as bull-run filter |
| Move quality | Filter/rank | Best next selector for clean 5-day trades |

## Recommended Next Feature: Move Quality

### Problem

The current console can select stocks that move a lot but move badly.

For a 5-day trade, a clean upward move is better than a violent stock that gives back gains every day.

### Proposed Field

Name:

`Move quality`

Plain meaning:

> Is the last 5-day movement clean and upward, or noisy and hard to hold?

### Simple First Rule

Look at the latest 5 completed daily candles.

Calculate:

- How many days closed higher than previous close.
- How many days closed near the day's high.
- Whether daily swings are too large compared with the actual progress.

First version can classify into three labels:

- `Clean`
- `Mixed`
- `Wild`

### Clean Example

Prices:

- Day 1 close: 100
- Day 2 close: 102
- Day 3 close: 103
- Day 4 close: 105
- Day 5 close: 106

Daily behavior:

- Mostly higher closes.
- Small pullbacks.
- Closes often near high.

Label:

- `Clean`

Interpretation:

- This is the type of movement I want for a 5-day trade.

### Mixed Example

Prices:

- Day 1 close: 100
- Day 2 close: 104
- Day 3 close: 102
- Day 4 close: 105
- Day 5 close: 106

Daily behavior:

- Upward overall.
- One meaningful give-back day.

Label:

- `Mixed`

Interpretation:

- Can review, but not first preference.

### Wild Example

Prices:

- Day 1 close: 100
- Day 2 close: 106
- Day 3 close: 101
- Day 4 close: 108
- Day 5 close: 103

Daily behavior:

- Big up/down movement.
- Progress is unreliable.
- Hard to hold for 5 days.

Label:

- `Wild`

Interpretation:

- Avoid unless there is very strong chart context in Compact Review.

### Guardrail

Do not overfit this rule.

The first version should not become a complex formula. It should answer one simple question:

> Is the stock moving in a way I can actually hold?

## Recommended Volume Feature: Exit Pressure

### Problem

A stock that has run for 15 to 20 days creates profit for earlier buyers. If a large volume day appears near the high, some of those holders may be exiting.

This is not confirmed distribution. It is only a caution signal.

### Proposed Field

Name:

`Exit pressure`

Labels:

- `Quiet`
- `Watch`
- `Caution`

### Rule Direction

Use last 1 to 2 sessions first, not only largest 5-day volume.

Why:

The user's observation is about recent holder exit after the run has already started.

### Examples

Quiet:

- 20D move: +18%
- Last 2 days volume: normal
- Price holding near high
- Label: `Quiet`
- Meaning: no obvious exit pressure yet.

Watch:

- 20D move: +18%
- Last 1 day volume: 3x normal
- Candle green or middle
- Label: `Watch`
- Meaning: large exchange of shares; be careful.

Caution:

- 20D move: +18%
- Last 1 day volume: 3x normal
- Close near low or red close
- Label: `Caution`
- Meaning: possible exit pressure and weak result.

Invalid interpretation:

- `Caution` means definitely sell.

Correct interpretation:

- `Caution` means do not enter without deeper review.

## Recommended UI Design

### Main Core Row

Core should be readable in one glance.

Recommended columns:

| Column | Example | Meaning |
| --- | --- | --- |
| No. | 1 | Review order |
| Stock | ABC | Symbol and company |
| Hit habit | `20D 8/20`, `Recent 4/6` | Can it move +5%? |
| Now | `5D +3.2%`, `20D +11.5%` | Is it active now? |
| Quality | `Clean` | Is movement holdable? |
| Exit pressure | `Quiet` | Are holders likely exiting? |
| Finish | `HIGH 82%` | Did latest day close strong? |
| Action | Review | Open deeper console |

### Visual Rules

Use color only for decision meaning:

- Green: clean/strong.
- Amber: caution/watch.
- Red: avoid/review carefully.
- Grey: context only.

Do not color every positive number green. A very large positive move can be late, not good.

Example:

- `5D +3%` can be green.
- `5D +14%` should be amber or red because entry may be late.

### Details Popup

Details should contain:

- Successful-day examples.
- Close-position bucket history.
- Recent high price and date.
- 52-week high price and distance.
- Exact high-volume day.
- Risk floor context.

Do not put all of this in the main table.

## Good vs Bad Trade Candidate Examples

### Strong Candidate

Data:

- `20D +5% hit`: 8/20
- `Recent tested 6D hit`: 4/6
- `5D move`: +3.5%
- `20D move`: +12%
- `From recent high`: -2%
- `Move quality`: Clean
- `Exit pressure`: Quiet
- `Latest finish`: HIGH

Interpretation:

- Good review candidate.
- Stock has short-term movement habit.
- It is still active.
- It has not obviously started exit pressure.
- Movement is easier to hold.

Action:

- Open Compact Review.
- Check chart structure before trade.

### Stale Candidate

Data:

- `20D +5% hit`: 10/20
- `Recent tested 6D hit`: 1/6
- `5D move`: -1%
- `20D move`: +4%
- `From recent high`: -8%
- `Move quality`: Mixed
- `Exit pressure`: Quiet
- `Latest finish`: MID

Interpretation:

- Was strong earlier, not strong enough now.

Action:

- Do not prioritize.

### Late Candidate

Data:

- `20D +5% hit`: 9/20
- `Recent tested 6D hit`: 5/6
- `5D move`: +14%
- `20D move`: +32%
- `From recent high`: -1%
- `Move quality`: Clean
- `Exit pressure`: Watch
- `Latest finish`: HIGH

Interpretation:

- Strong, but maybe late.
- It can still run, but entry risk is higher.

Action:

- Review only if there is a fresh pause or clean continuation setup.
- Do not chase blindly.

### Distribution-Warning Candidate

Data:

- `20D +5% hit`: 8/20
- `Recent tested 6D hit`: 4/6
- `5D move`: +8%
- `20D move`: +22%
- `From recent high`: -3%
- `Move quality`: Mixed
- `Exit pressure`: Caution
- `Latest finish`: LOW

Interpretation:

- Movement exists, but recent large volume plus weak finish warns that holders may be exiting.

Action:

- Avoid for quick trade unless Compact Review gives a strong reason.

### Wild Candidate

Data:

- `20D +5% hit`: 11/20
- `Recent tested 6D hit`: 5/6
- `5D move`: +6%
- `20D move`: +16%
- `From recent high`: -5%
- `Move quality`: Wild
- `Exit pressure`: Watch
- `Latest finish`: MID

Interpretation:

- It moves, but the movement is hard to trade.

Action:

- Lower priority than a clean mover.

## What Should Be Changed First

### Priority 1: Add Move Quality

Reason:

This is closest to the latest understanding. It separates clean upward movement from noisy movement.

Recommended role:

- Filter/rank.

Do not make it complex in the first version.

### Priority 2: Convert Volume Into Exit Pressure

Reason:

The current volume column shows data, but the user wants an interpretation:

> Has crowd exit likely started after the run?

Recommended role:

- Warning, not filter.

### Priority 3: Rename Recent 6D

Reason:

The current label can be misunderstood.

Recommended label:

- `Recent tested 6D`

### Priority 4: Simplify Core UI

Reason:

The Core tab should be the trading review list, not another wide analytics table.

Recommended change:

- Main row gets only the fields needed for first decision.
- Move exact evidence into Details.

### Priority 5: Keep Risk Floor As Details

Reason:

Risk floor is useful, but in bull runs it can incorrectly reject clean winners.

Recommended role:

- Details/context only.

## What Should Not Be Added Yet

Do not add RSI yet.

Reason:

It adds another interpretation layer without directly solving the current problem. The console already has 5D and 20D movement.

Do not add a composite score yet.

Reason:

A score hides why a stock passed. The user wants clear reason-per-data-point logic.

Do not add automatic buy/sell labels.

Reason:

This is a selector. Compact Review and trader judgement still matter.

Do not use volume spike as confirmed distribution.

Reason:

One or two large volume bars are evidence of activity, not proof of distribution.

## Proposed Future Workflow

1. Select watchlist.
2. Open Core.
3. Scan only rows marked:
   - good hit habit,
   - active now,
   - clean move quality,
   - no exit-pressure caution.
4. Open Compact Review for only 3 to 5 stocks.
5. Decide trade from chart context, not from selector alone.

## Acceptance Criteria For Next Console Version

The next version should be accepted only if:

- Core row can be understood in under 3 seconds.
- Every visible column has one clear purpose.
- No field has two possible meanings.
- Volume is shown as caution/warning, not confirmed distribution.
- Risk floor is not used as a bull-run rejection rule.
- Clean upward movement is preferred over noisy movement.
- The console reduces a 100-stock watchlist to a small review list without pretending it has found guaranteed trades.

## External Risk Notes

Short-term trading requires strict risk control. Public investor guidance from SEC and FINRA warns that frequent short-term trading can produce large losses, especially when traders chase moves without a plan. NSE price bands also matter in India because some stocks may have daily movement limits, which can affect exit behavior.

Reference links:

- SEC day trading risk note: https://www.sec.gov/about/reports-publications/investorpubsdaytipshtm
- FINRA frequent intraday trading note: https://www.finra.org/investors/insights/frequent-intraday-trading
- NSE price bands: https://www.nseindia.com/static/products-services/equity-market-price-bands

## One-Line Product Principle

The selector should find stocks with proven short-term movement, current clean strength, and no obvious crowd-exit warning; everything else belongs in details or Compact Review.
