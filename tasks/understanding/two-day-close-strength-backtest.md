# Two-Day Close-Strength Backtest

This experiment tests a weekly five-session sequence: the first three trading sessions must finish below the 80% close-position threshold, and the final two sessions must finish at or above 80% of their daily high-low range. A qualifying week enters on the next available session's open, normally the following Monday.

The trade uses a fixed 5% target. The target is checked using intraday highs through Wednesday of the entry week; if it is not reached, the position exits at Thursday's close, or the next available session's close when Thursday is a market holiday. The report uses the existing saved watchlist source and a six-month lookback, excludes the current incomplete week, and reports realized returns including losses.
