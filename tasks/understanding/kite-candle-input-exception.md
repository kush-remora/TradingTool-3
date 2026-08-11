# Kite candle InputException

Hot SMA fails before caching because the shared candle source sends the instrument token stored with an index constituent directly to Kite. The previous working path resolved the current token by NSE symbol first, so a stale stored token now causes Kite's unhelpful `InputException`.

Fix the shared source, not Hot SMA specifically: load the current Kite instrument catalogue and always resolve the symbol's current token before requesting candles. The stored token is used only to detect and log a mismatch. Add focused token-selection coverage, run the Maven tests, and review the Kotlin diff.

Implemented in the shared `CandleDataService`. The regression test and full Maven reactor pass; Kotlin and general code review found no blocking issues.

The RSI oversold backtest exposed a second boundary condition: Kite accepts only about 1,900 calendar days for one daily-history request, while an older user-selected test start plus the five-year RSI warm-up can exceed that limit. The shared source now splits daily requests into 1,800-day inclusive chunks, uses the current time when the requested end date is today, and adds symbol/token/range context when Kite rejects a request.
