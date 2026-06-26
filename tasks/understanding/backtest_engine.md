# Backtesting Engine: Previous Day Low Trailing Stop Loss

## Problem Statement
The user wants to build a backtesting engine that ingests a CSV list of daily stock picks and simulates a specific trading strategy to evaluate performance, grouped by market cap and sector.

## Strategy Rules
- **Input**: A CSV containing `date`, `symbol`, `marketcapname`, `sector`.
- **Entry**: Buy ₹10,000 worth of the stock at the market open on the trading day *following* the date in the CSV.
- **Initial Stop Loss**: The low price of the date in the CSV (the day before entry).
- **Trailing Stop Loss**: Update the stop loss every day to the previous day's low.
- **Exit**: Sell when the stock price hits or drops below the active stop loss.
- **Reporting**: Aggregate and report the profit/loss (P&L) grouped by `marketcapname` and `sector`.

## Open Questions
1. **Data Source**: Where will the historical OHLC (Open, High, Low, Close) data come from to run the simulation? Should the engine fetch this automatically (e.g., using `yfinance` or NSE bhavcopy data), or will it be provided in another file?
2. **Trailing Stop Logic**: When updating the stop loss daily to the "previous day's low", should it *only* move up (standard trailing stop), or strictly follow the previous day's low even if it drops?
3. **Position Sizing**: Should we buy an integer number of shares that closely approximates ₹10,000, or allow fractional shares (which are not standard for Indian equities)?
4. **Execution Price**: For exits, if the market gaps down below the stop loss at the open, should we assume the exit price is the stop loss price or the open price of that day?
5. **Commissions & Slippage**: Should we factor in brokerage, STT, and slippage into the P&L calculation?
