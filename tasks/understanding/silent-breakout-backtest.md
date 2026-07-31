# Silent Breakout Backtest — Working Understanding

Kush wants a dedicated backtest UI for a Chartink-exported quiet-participation signal. The starting condition shown in Chartink is: daily volume exceeds a selected multiple of its prior 20-day volume SMA while the absolute one-day close-to-close move is at most 3%. The hypothesis is that, when this occurs outside a Wyckoff distribution structure, it can precede a classic silent upside breakout.

The existing CSV backtest is not a faithful evaluator because it always requires a fresh breakout on the signal date. The feature will be a dedicated backtest page, but it should reuse the existing CSV upload, controls, results table, and Trade Details drawer patterns rather than create a separate application shell. It accepts historical screener exports as independent signal files, but does not need to know or compare the screening volume multiple.

## Confirmed Input Contract

- Each uploaded row is only a `symbol` and `signal date`; optional source columns may be retained for display but do not affect analysis.
- A user may upload different historical screener files for separate backtest runs. The tool evaluates every imported signal independently, without needing to understand the file's volume rule.
- The Chartink condition that produced a file remains external to this backtest. Its quiet green/red candle definition is not recomputed here.
- The signal date is an observation date, not an entry or proof of a breakout.

## Classification Direction

One imported signal cannot objectively prove accumulation or distribution. Kush prefers to judge the Wyckoff structure by viewing the chart, rather than encoding an automatic classification. V1 should therefore retain all candidates, expose only an auditable `LATE_STAGE_RISK` context flag, and provide a manual Wyckoff label: `ACCUMULATION`, `DISTRIBUTION`, or `UNCLEAR`. The backtest must never use future candles to assign this label; future candles are used only to measure the outcome.

Distance from the 52-week high is valuable as a visual-review priority, not as an automatic distribution verdict: valid Phase D breakouts can also occur near that high. The agreed initial late-stage-risk flag is deliberately simpler: a 20-day price advance of at least 20%. The table must still show 52-week-high distance and 200-SMA distance, retain every signal, and let Kush assign the Wyckoff verdict after chart review.

The likely signal-date context inputs are: distance from the 52-week high, date of that high, 20-day ROC, distance from the 200 SMA, position within the prior 60-day range, daily spread relative to its 20-day average, close location within the day's range, and count of prior quiet high-volume days.

## Open Decisions

- The forward outcome horizon, breakout definition, and comparison baseline.

## Implemented Decision

The dedicated Silent Breakout Backtest page accepts a CSV containing `symbol` and `date`, runs point-in-time context analysis, and displays 20/40-session outcomes. The late-stage-risk flag is `ROC20 >= 20%`; it remains a chart-review warning rather than an automatic Wyckoff classification. The table exposes an in-session manual Accumulation/Distribution/Unclear verdict. Stocks with less than 252 sessions are analysed using all available history and marked `Partial history`; they are not excluded.

Each run accepts a target percentage. The simulated entry is the next trading session's open. The target check uses each following session's high for up to 40 sessions and reports whether it was achieved and how many trading days it took.

For shakeout-pattern research, the table also reports the lowest low in the first five post-signal sessions, its percentage move from the signal close, and the session number of that low. These are observational metrics, not a tradable bottom-entry simulation.

Blind review is the default table mode. It hides all post-signal values, including simulated entry, target, dip, and future returns, so manual chart review and Wyckoff labelling are not biased by the outcome.
