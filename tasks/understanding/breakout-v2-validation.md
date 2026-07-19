# Breakout V2 Validation

## Confirmed Rules

1. **Base breakout event:** `high(D) > max(high(D-100..D-1))`. The comparison uses 100 completed trading sessions and a strict greater-than condition.
2. **Fresh breakout:** show only the first breakout event in a 100-session run. A day is not fresh when the same stock already satisfied the base breakout event in the preceding 99 sessions. This prevents repeated higher-high days from being presented as new breakouts.
3. **Pre-breakout volume participation:** inspect only `D-20..D-1`; the breakout candle is excluded. For each inspected day `X`, calculate `volume(X) / average(volume(X-20..X-1))`. Require at least one day with a ratio of `2.0` or more. The largest ratio is returned as strength information.
4. **Extension guard:** require `close(D) <= close(D-1) * 1.06`. This avoids chasing a breakout already more than 6% above the prior close. It is deliberately not called a distribution test.
5. **Failed resistance attempts:** the resistance level is the prior 100-session high. Count one attempt per upswing when its high reaches at least `97%` but remains below `100%` of that level, and its close remains below it. Consecutive qualifying sessions belong to one attempt, so they do not inflate the count.

## Output / Strength Information

- fresh-breakout date and age in sessions;
- maximum pre-breakout volume ratio;
- number of failed resistance attempts in the prior 100 sessions;
- breakout level, current high, close, and close-to-close percentage change.

## Confirmed Recent-Run Measurement

1. Within the 30 sessions ending on the breakout day, find the session with the lowest daily low.
2. Select that session and the five trading sessions immediately before it: six sessions total. No future sessions are used.
3. Calculate their average closing price and name it `recent_run_base_price`.
4. Report `move_from_recent_base_pct = (close(D) - recent_run_base_price) / recent_run_base_price * 100`.

This measures how far the stock has already moved above a smoothed recent base, without a one-day wick distorting the result.

## Not Included Yet

A true distribution warning is not part of V2. Distribution needs supply-over-demand evidence such as unusually high volume with poor progress and a close near the daily low, usually at the top of a range. The 6% rule is only an extension guard.

## Implementation Plan

1. Add a pure `CsvBacktestV2Validator` beside the existing entry/exit evaluators. It will recalculate the five hard conditions from daily candles and return the recent-base move, volume ratio, and failed-attempt count.
2. Extend the existing CSV-backtest request and response contract with an optional V2 switch and its metrics. When enabled, only validated signals reach the existing entry/exit simulation; V1 remains unchanged by default.
3. Extend the existing **CSV Backtesting Engine** form with one compact `Apply V2 breakout validation` switch. Its result table will show the V2 strength fields and the summary will show submitted versus validated signal counts.
4. Add focused validator tests for each rule and run Kotlin tests, backend compile, frontend tests/build, then perform Kotlin and general code-review passes.

## Delivered 2026-07-20

V2 is implemented as an opt-in validation switch in the existing CSV Backtesting Engine. It loads sufficient historical candles, excludes signals that fail any hard V2 condition before entry simulation, and returns the pre-breakout volume ratio, failed-test count, six-close base price, and move from that base in the trade table. The results header shows submitted signals and signals passing V2, while a disabled switch preserves V1 behaviour.

The results header also supports a client-side **Maximum V2 Run %** filter. It hides trade rows above the supplied move-from-base percentage without rerunning the backtest; clearing it restores every trade.

## Green Confirmation Entry (In Progress)

Add a selectable entry strategy alongside next-day open and retest entries. The breakout session counts as green candle #1 when its close exceeds the previous close. Scan up to 20 later sessions for green candle #2, defined as a close above the immediately preceding close; buy at the following session's open. The earliest entry is T+2 open. Also return the number of red sessions—close below the previous close—across the buy session and its next two sessions.

Delivered: the existing entry-rule selector now includes `2 Green Candles`. It rejects a non-green breakout, waits for the next green close for at most 20 sessions, then enters at the following open. The trade table includes `3D Red Candles`, counting the buy candle and its next two sessions. Focused Kotlin tests, backend compilation, and the frontend production build passed.

Validation passed: focused CSV-backtest Kotlin tests, `mvn -q -pl resources -am -DskipTests compile`, and `npm --prefix frontend run build`. The code-review pass found no critical or high-confidence issues.
