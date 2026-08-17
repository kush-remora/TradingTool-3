# Adaptive breakout six-month test

The user wants a small validation lab for one selected NSE stock. It replays the existing adaptive-breakout engine over the latest six calendar months, treats each completed `FRESH_BREAKOUT` close as a signal, and reports how a simple fixed-risk trade would have behaved. This is a test of the current breakout definition, not a replacement strategy and not a live order workflow.

To avoid look-ahead, a signal is only known after its breakout-day close, so the simulated entry is the next available session's open. The entry gets a fixed +5% target and -5% stop. Daily OHLC cannot prove whether a target or stop was hit first when both are inside one candle; the evaluator will use the conservative stop-first assumption and label that case. An open position is closed at the last available close as `END_OF_TEST`, and only one position is held at a time. The page will show the exact signal, entry, exit, reason, return, and holding sessions so the user can inspect every result rather than relying on an opaque score.

API contract: `POST /api/strategy/adaptive-breakout/backtest/run` with `{ symbol, instrumentToken, months?, targetPct?, stopLossPct? }`. Defaults are six months, 5% target, and 5% stop. The backend reuses the same adaptive-breakout configuration and candle cache as the scanner; it does not introduce a second ceiling algorithm.
