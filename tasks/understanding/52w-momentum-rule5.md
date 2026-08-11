# 52W Momentum Rule 5

Rule 5 is being simplified from a CSV-based 200-SMA proximity analysis into a watchlist scan. The page lets Kush select one or more active watchlists and one breakout period: 20D, 40D, 60D, 100D, or 200D. For a selected N, the breakout reference is the highest daily high across the preceding N trading sessions. A strict fresh breakout requires today's close to be above that reference high. A configurable near-high tolerance allows today's close to be up to X% below the reference high; for example, a prior 200-session high of ₹100 and a current close of ₹98 qualifies with a 2% tolerance. The signal is emitted when price first enters that accepted band, preventing repeated signals while it remains in the same band. The result window remains the latest five trading sessions. Selected watchlists are unioned and duplicate symbols are evaluated once.

The same signal rule supports a six-month historical backtest. An entered trade buys at the breakout-day close, targets 10%, exits on the first later trading day whose high reaches the target, and remains open when the target is not reached by the backtest end. Only one position per stock is active at a time; later signals while it is open are retained as skipped signals in detailed results but are excluded from the entered-trades tab.

Entered trades also show the latest available close through the backtest end date as LTP, plus the percentage move from the entry price. This is a historical backtest-end mark, not a live intraday quote.

Each entered-trade symbol carries its instrument token and links to the corresponding NSE chart in Kite, opening in a new tab.

The tolerance is applied consistently to live scans and backtests. The backtest enters at the signal day's close, so a near-high signal is intentionally treated as a close-confirmed proximity event rather than an intraday high touch.

Each signal also reports how many trading sessions ago the most recent occurrence of the reference high happened. This age is measured within the selected N-session window and uses the latest occurrence when the same high was printed more than once.

The scan is intentionally limited to this price-event rule. It does not use SMA distance, volume, delivery, market cap, sector, or close-confirmation filters. Continuation highs after a recent breakout are excluded. The result should show each matching stock once, with its selected watchlists and each fresh breakout date/high/reference close so the five-session result is easy to verify.
