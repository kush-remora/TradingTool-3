# Friday Close-Strength Weekly-Upside Backtest

Status: retired. The experiment was removed after the six-month sample did not provide sufficiently reliable evidence for keeping it as a product feature.

The backtest tests whether a stock that closes near the high on Friday and has gained more than 2% from Thursday's close tends to offer upside during the following trading week. It uses the existing Summary Console watchlist/index membership source, supports one selected watchlist, and defaults to the most recent six months. For each qualifying Friday, the simulated entry is the next available trading session's open (normally Monday); the result is the maximum favorable excursion through that week's Friday, using the highest intraday high observed after entry.

The Friday signal is `((Friday close - Friday low) / (Friday high - Friday low)) >= 70%` plus `((Friday close / Thursday close) - 1) * 100 > 2%`. A zero-range Friday is not a signal. Holidays use the next available session for entry, and the following week ends at the last available session before the next Monday. Alongside the retrospective maximum movement, the realistic result uses a fixed 5% target: if the target is reached by Wednesday's high, exit at the target; otherwise exit at Thursday's open (or the next available session if Thursday is a market holiday). The report must show realized returns, including losses, and preserve one row per stock-Friday signal for auditability.

Each signal also records Friday volume against the average volume of the prior 10 trading sessions: `((Friday volume / prior-10-session average volume) - 1) * 100`. This is reported as percentage change above or below the baseline, with the raw Friday volume and baseline retained for auditability.

The audit table is intentionally dense: compact rows, one-line exit details, and a viewport-height scroll area keep many signals visible together without pagination.
