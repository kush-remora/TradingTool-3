# Weekly Scanner Logic V2

**Date:** April 2026  
**Purpose:** Define the corrected weekly scanner model in plain language so the product, backend, and UI all follow the same trading logic.

## 1. Why We Are Changing It

The earlier weekly scanner model mixed together too many ideas:

- buy day
- sell day
- target %
- average weekly swing

That created confusing outputs such as:

- showing Friday as the "sell day" even when targets were usually hit on Tuesday
- showing average swing numbers that did not match the actual trade logic
- making the scanner look like it predicts the whole week, when in reality we only need one clear entry decision

The scanner should not try to tell us every important day of the week.
It should answer one main question clearly:

**If today is this stock's buy day, should I take the trade or skip it?**

## 2. What The Weekly Scanner Wants To Achieve

The goal is to make the weekly setup actionable and simple:

1. Identify the single weekday that gives the best actual swing-trade entry setup.
2. Wait for a `+1% rebound` from that day's low before entering.
3. Skip the trade if RSI is too high for entry.
4. After entry, use pre-defined targets like `5%`, `6%`, or `7%`.
5. Avoid making new decisions for the rest of the week.

This means the scanner is a **buy-day decision system**, not a full-week prediction engine.

## 3. Core Product Mental Model

The correct mental model is:

- `Monday` or `Tuesday` or `Wednesday` is the stock's buy day.
- On that day, we watch for a `+1% rebound` from the day's low.
- If RSI is too high in its recent range, we do nothing.
- If the rebound never happens, we do nothing.
- If entry happens, the trade is already defined:
  - target = `5%` or `6%` or `7%`
  - stop-loss = fixed rule
- We do not keep checking the stock every day to make new decisions.

In simple terms:

> "Today is the buy day. If the setup triggers, buy it. If not, skip it. After that, let the predefined trade play out."

## 4. What The Scanner Should Tell The User

For each stock, the UI should clearly show:

- `Buy Day`
- `Entry Rule`
- `RSI Overbought Rule`
- `Trade Today: Yes / No`
- `Blocked Reason` if no trade
- `5% target hit chance`
- `6% target hit chance`
- `7% target hit chance`
- `Typical hit timing` as supporting context only

Example:

- `Buy Day: Tuesday`
- `Entry Rule: Buy only after +1% rebound from Tuesday low`
- `RSI Overbought Rule: skip only near the 50D RSI high`
- `Trade Today: Yes`
- `5% Target: 68% hit rate`
- `6% Target: 54% hit rate`
- `7% Target: 31% hit rate`
- `Typical Hit Timing: Mostly Tue or Wed`

The main message should be:

**"Today is the setup day. Here are the target odds if the setup triggers."**

## 5. The Weekly Scanner Logic

### Step 1: Find The Buy Day

Look at completed historical weeks and test candidate buy days directly.

The scanner should evaluate `Monday`, `Tuesday`, and `Wednesday` as real trade setups, not just as abstract low days.

For each candidate day, it should measure:

- how often the `+1% rebound` entry actually triggers
- how often the chosen targets are hit
- how often stop-loss is hit
- what the average realized outcome looks like

The buy day should be the day with the best actual trade performance.

This is important because the older logic had a Monday bias:

- it looked too much at which day was the weekly low
- it favored days with more time left in the week
- that made Monday appear too often even when it was not the best real trade day

In practice, this will usually be one of:

- Monday
- Tuesday
- Wednesday

The output must be a **single buy day**, not a vague sentence like "some weeks Monday, some weeks Friday."

### Step 2: Apply The Entry Rule

On the buy day:

- calculate that day's low
- wait for price to rebound by `+1%` from that low
- enter only if that rebound actually happens

If the rebound does not happen, there is no trade for that week.

### Step 3: Apply The No-Trade Filter

Even if it is the buy day, we still skip the trade when RSI is truly near its recent extreme high.

The scanner now uses the last `50` trading days of `RSI(14)` to decide whether the stock is overbought.

Current rule:

- calculate `RSI(14)` for each of the last `50` trading days
- find the `50D` lowest RSI and highest RSI
- place today's RSI inside that `50D` range
- `Overbought`: current RSI percentile is `>= 90%` of the `50D` range

That means we do **not** skip trades just because RSI is elevated.
We skip only when RSI is very close to its recent high.

Example reasons for `No Trade`:

- RSI overbought
- 1% rebound never triggered
- today is not the stock's buy day

### Step 4: Evaluate The Target Ladder

If entry happens, the system should evaluate target ladders such as:

- `5%`
- `6%`
- `7%`

For each target, we want to know:

- hit rate
- average realized return
- stop-loss frequency

This is more useful than forcing a fake sell day.

### Step 5: Treat Hit Timing As Context, Not The Decision

It does not matter whether the target is hit:

- same day
- next day
- two days later

That timing is still useful to show, but only as context.

It should never override the main model.

So:

- `Buy Day` is the decision
- `Target %` is the reward choice
- `Typical Hit Day` is supporting information

## 6. What The Scanner Is Not Trying To Do

The scanner is **not** trying to:

- predict the exact highest day of the week
- guarantee a trade every week
- force a trade when RSI is too high
- keep changing the plan after entry
- optimize around a fixed "sell day" as the main output

That older framing was misleading.

## 7. Historical Evaluation Rules

When we backtest this logic, each week should be evaluated like this:

1. Check whether the week contains the stock's defined buy day.
2. On that day, check whether the `+1% rebound` entry was triggered.
3. Check whether RSI is near the `50D` maximum RSI zone.
4. If RSI is overbought, mark the week as `No Trade`.
5. If entry happened, evaluate whether `5%`, `6%`, or `7%` would have been hit later in the week.
6. Record:
   - entry happened or not
   - target hit or not
   - stop-loss hit or not
   - typical day target was reached

This gives us the numbers we actually care about:

- how often a valid setup appears
- how often each target is hit
- what the payoff looks like after entry

## 8. UI Interpretation Rules

The UI should be read in this order:

1. `Is today the buy day?`
2. `If yes, did the +1% rebound trigger?`
3. `If yes, is RSI still below the overbought cutoff?`
4. `If yes, which target ladder do I want to use: 5%, 6%, or 7%?`

After that, the week is already planned.

This means the UI should feel like a checklist, not a research dashboard.

## 9. Example Of The Intended User Flow

Example:

1. Today is Tuesday.
2. The scanner says this stock's buy day is Tuesday.
3. Price rebounds `+1%` from Tuesday's low.
4. RSI is not near the `50D` high.
5. User takes the trade.
6. User already knows the target probabilities.
7. User does not need to keep re-analyzing the stock for the rest of the week.

That is the intended workflow.

## 10. Success Criteria For V2

The weekly scanner is successful if it helps the user answer these questions instantly:

- `Is today the day I should watch this stock?`
- `What exact trigger tells me to buy?`
- `If I buy, what are the odds of hitting 5%, 6%, or 7%?`
- `If the setup is invalid, why am I skipping it?`

If the UI answers those questions clearly, the scanner is doing the right job.

## 11. Final Principle

The weekly scanner should produce **one clear weekly action**, not a complicated market story.

The correct output is:

- a fixed buy day
- a fixed entry trigger
- an overbought veto near the `50D` RSI high
- a no-trade filter
- a target probability ladder

Everything else is secondary.
