# CSV Backtesting Tool - Product Understanding

## Problem Statement
The user needs a flexible backtesting engine that ingests a list of signal dates and stock symbols via CSV and evaluates trade performance. The tool must simulate trading realistically using only daily candles, properly accounting for next-day open entries, overnight price gaps, and providing both fixed Target/SL and Trailing SL strategies.

## User Story
As a trader, I want to upload a CSV of stock signals (date, symbol, market cap, sector) and configure backtest parameters (fixed target/SL % or trailing SL %) from the UI. I want to see trade-by-trade results including entry/exit dates, entry/exit prices, profit/loss, and holding periods. I also want a monthly summary of performance so I can evaluate the effectiveness of the strategy that generated the CSV signals.

## Acceptance Criteria
1. **CSV Ingestion**:
   - Accepts CSV with `date`, `symbol`, `marketcapname`, `sector`.
2. **Trade Entry Rule**:
   - Buy on the **Open** price of the *next available trading day* after the signal date.
3. **Exit Strategies (2 Modes)**:
   - **Mode 1: Fixed Target & Stop Loss**
     - Target % and Stop Loss % configured in the UI.
   - **Mode 2: Trailing Stop Loss**
     - Trailing SL % configured in the UI.
     - SL ratchets upward based on the **Highest Close** since entry. SL never moves down.
4. **Execution Logic (Daily Candles)**:
   - **Gap Down (SL Hit)**: If the day's Open is below the SL, exit at the Open price.
   - **Gap Up (Target Hit)**: If the day's Open is above the Target, exit at the Open price.
   - **Intraday SL Hit**: If Low <= SL, exit at SL price.
   - **Intraday Target Hit**: If High >= Target, exit at Target price.
   - **Conflict (Target & SL hit on same day)**: Assume SL was hit first (conservative).
5. **Open Trades**:
   - Trades that never hit SL/Target by the end of available data remain "Open".
6. **Outputs**:
   - **Trade List**: Signal Date, Entry Date, Entry Price, Exit Date, Exit Price, P&L %, Days Held, SL Hit (boolean).
   - **Monthly Summary**: Total Trades, Win Count, Loss Count, Avg Holding Period, Avg P&L %.

## Technical Considerations
- **Data Source**: Re-use `CandleDataService` and `CandleReadDao` for fetching historical daily candles. 
- **Backtest Engine**: Create a dedicated Kotlin engine/service to process the CSV rows concurrently, fetch daily candles, and simulate the execution rules.
- **API & UI**: Build a new Next.js page with a CSV uploader, configuration inputs (Strategy type, Target %, SL %), a detailed Ant Design table for trades, and a summary section for monthly aggregations.

## Out of Scope
- Intraday (15-minute) tick-by-tick simulation (sticking to Daily OHLC).
- Short selling backtests (long only for now).
- Portfolio position sizing/capital allocation (tracking raw percentages only).

## Complexity Estimate
Medium-High. Involves a new UI page, new backend endpoints, a state machine for the backtest engine (handling gaps, conservative exits, trailing logic), and integrating the CSV parser.
