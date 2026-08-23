# Four-Day Close-Strength Backtest — Understanding

The requested feature is a price-action backtest for either one NSE stock or an existing watchlist. A signal is a rolling four-trading-session window in which every candle closes above its open and each close is at or above a configurable percentage of that session's high-low range. The default close-location threshold is 80%, and the default lookback is the latest 60 calendar months.

The first slice is an inspection tool, not a trading simulator. For every signal date, the response will include the four signal candles plus the preceding sessions needed to show the last 20 trading sessions ending on that signal date. The UI will show signal dates, close-location values, and a 20-day expandable detail table. Stock symbols will link directly to the corresponding NSE Kite chart. Watchlist results will remain stock-wise and sortable, with no speculative target, stop, volume, or de-duplication rule.
