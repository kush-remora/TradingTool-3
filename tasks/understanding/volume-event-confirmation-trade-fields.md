# Volume Event Confirmation Backtest Trade Fields

The Volume Event Confirmation Backtest now has one rule and one compact result table. It selects one or more watchlists, runs the default six-month window, and uses an editable target percentage defaulting to 10%.

The latest available candle close at the backtest end date is the historical Current LTP reference, regardless of whether the trade already hit its target or remains open. The API returns the percentage change from entry price to that Current LTP for both outcomes.

Rows are created only for actual entries: today must be a new volume shocker at least 2× the prior five-session average, a previous qualifying shocker must be at least five trading sessions earlier, today's close must be below the previous shocker close, and entry occurs at the next session open. A target-hit row has exit details; an open row keeps Current LTP and percentage change, has no exit date or exit price, and remains open.

The UI exposes no RSI selector or alternate entry mode.
