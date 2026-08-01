# Kite candle InputException

Hot SMA fails before caching because the shared candle source sends the instrument token stored with an index constituent directly to Kite. The previous working path resolved the current token by NSE symbol first, so a stale stored token now causes Kite's unhelpful `InputException`.

Fix the shared source, not Hot SMA specifically: load the current Kite instrument catalogue and always resolve the symbol's current token before requesting candles. The stored token is used only to detect and log a mismatch. Add focused token-selection coverage, run the Maven tests, and review the Kotlin diff.

Implemented in the shared `CandleDataService`. The regression test and full Maven reactor pass; Kotlin and general code review found no blocking issues.
