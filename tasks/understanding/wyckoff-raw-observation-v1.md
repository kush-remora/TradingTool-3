# Wyckoff Raw Observation V1

This feature is an observation aid for the investing workflow, not an automated Wyckoff classifier. The scanner accepts one NSE watchlist or one stock and returns raw daily OHLCV data plus a few transparent measurements: daily change, daily range, true range, 14-session ATR, ATR as a percentage of price, true-range-to-ATR, volume versus the prior 20 sessions, 20-session price change, 20-session range, and the prior 20-session high. A human reviews the table alongside a one-year chart and volume to decide whether the price/volume behavior is worth investigating.

V1 deliberately has no score, pass/fail result, confirmed accumulation label, confirmed phase label, 200 SMA rule, or breakout recommendation. It now adds simple candidate timeline annotations: prior decline, candidate accumulation, orderly advance, and possible Phase D. These are explainable prompts tied to the raw numbers, not conclusions. The table is the feedback loop: inspect the periods and dates, find missing context or bugs, and only then decide which additional columns or later interpretation rules deserve to exist. The old verbose Wyckoff Phase 1 implementation remains separate and is not reused for this experiment.

## Current outcome

Implemented a typed Kotlin calculation engine, watchlist/symbol service, API endpoints, and an expandable frontend table with candidate-period timelines and day-level explanations. The focused engine test covers decline → candidate accumulation → possible Phase D. The service-layer compile and frontend production build pass. Next step is human review of real watchlist output and iterative adjustment of the annotations and raw columns.
