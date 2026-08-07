# Two-Day Green-Candle Backtest

The requested first version tests a simple momentum hypothesis for a selected stock or watchlist: on a candidate day, the two immediately preceding completed daily candles must each close above their open and gain more than 1% close-to-close. The strategy enters at the candidate day open and uses a fixed 5% target measured from that open. The initial analysis is limited to the latest 40 trading sessions and must show, without filtering on them, volume progression and volatility observations for the two setup days and the buy day.

Implementation decisions: the buy day is not filtered using its own OHLCV because those values are unknown at the open; its completed candle is displayed for observation only. Volume progression means day T-1 volume compared with day T-2 volume. Intraday volatility is reported as open-to-close percentage and low-to-high range percentage. A target is considered hit when a daily high reaches the target; if the target is not reached before available data ends, the trade remains open and is marked unresolved rather than inventing an exit price.

Validation will cover setup detection, target timing, unresolved trades, rolling 40-session scope, and observation metrics. The feature will be exposed through the existing strategy API and frontend watchlist backtest workflow.
