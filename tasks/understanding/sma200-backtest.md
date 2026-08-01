# SMA200 limit-entry backtest

The requested feature is a simple single-stock, one-year backtest based on the existing SMA Buy Zone principle. The user selects one NSE equity, and the engine evaluates each trading day in the latest one-calendar-year window. If that day's low reaches the exact daily SMA200, it assumes a limit-order fill at SMA200. A filled trade remains active for 40 trading sessions, so later SMA200 touches during that period do not create another trade. Returns are measured from the fill price to the close exactly 10, 20, and 40 trading sessions later; unavailable future closes remain empty for recent entries.

The entry row should preserve the existing screener context at the entry date: entry date and price, close, SMA100/SMA200 and percentage distances, RSI14, drawdown from the recent 20/60-day highs, consecutive red days, and 3-day move. The implementation should remain a small extension of the existing Kotlin strategy/API and React frontend, with a stock dropdown using the existing NSE instrument search.

The dropdown investigation confirmed that the running backend had an empty instrument cache because its Kite token had expired. The stock-instrument endpoint now retries loading the NSE cache when it is empty and returns a clear Kite-login-required error when authentication is unavailable. The shared dropdown no longer describes an empty API result as “All stocks already added.”

The SMA Buy Zone Screener now has a client-side Golden Buy Zone quick filter. It includes only rows where the scanner confirmed that the daily low touched SMA200 within the latest five trading sessions, and it exposes that yes/no condition as a table column for review.

The limit-entry backtest now needs an explicit entry-SMA choice. SMA200 remains the default, while SMA100 and SMA50 use identical daily-low touch, exact-limit fill, 40-session one-trade lockout, and 10/20/40-session return rules. The selected SMA period must be part of both request and response data so the result table cannot be misread.
