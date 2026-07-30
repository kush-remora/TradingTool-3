# CSV Backtest Breakout-Close Reclaim — Understanding

Add a CSV-backtest entry rule for post-breakout weakness: use the breakout/signal day's closing price as the reclaim level. Inspect the next 30 trading sessions, select the first session that closes strictly above that level, and buy at the following trading session's open. If no qualifying close occurs within those 30 sessions, reject the trade. A qualifying close on session 30 remains valid; its next-session open is the fill.

The rule belongs in the existing `CsvBacktestEntryEvaluator` and is exposed as a new entry-rule choice in the existing CSV page. It needs focused evaluator coverage for immediate confirmation, delayed confirmation, the 30-session boundary, and missing confirmation/entry data. No new API field or persistence is needed.

## Implementation Outcome

Implemented as `BREAKOUT_CLOSE_RECLAIM`. The evaluator records the signal day's close, checks only the first 30 later trading sessions for a strictly higher close, and fills at the following session's open. The UI labels the rule as `Breakout Close Reclaim (30d)`. Regression coverage verifies a delayed reclaim, acceptance on the 30th session, and rejection when the first reclaim is on session 31.
