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
   - Evaluate the entry-day candle as soon as the position is opened at its Open.
   - **Gap Down (SL Hit)**: If the day's Open is below the SL, exit at the Open price.
   - **Gap Up (Target Hit)**: If the day's Open is above the Target, exit at the Open price.
   - **Intraday SL Hit**: If Low <= SL, exit at SL price.
   - **Intraday Target Hit**: If High >= Target, exit at Target price.
   - **Conflict (Target & SL hit on same day)**: Assume SL was hit first (conservative).
5. **Open Trades**:
   - Trades that never hit SL/Target by the end of available data remain "Open".
6. **Multiple Files**:
   - Allow several Chartink CSV files to be selected together and backtest their combined signal rows.
7. **Outputs**:
   - **Trade List**: Signal Date, Entry Date, Entry Price, Exit Date, Exit Price, P&L %, Days Held, SL Hit (boolean).
   - **Monthly Summary**: Total Trades, Win Count, Loss Count, Avg Holding Period, Avg P&L %.

## Implementation Plan

1. Extract the fixed exit evaluation into a pure Kotlin helper and cover entry-day target/stop and same-candle stop-first behavior with unit tests.
2. Use that helper from the existing CSV service while retaining the current candle sync/data-source path.
3. Update the existing console uploader to accept and combine multiple CSV files without changing the backend API contract.
4. Run focused Kotlin tests, the service build, and the frontend production build; record the outcome in the feature journal.

## Technical Considerations
- **Data Source**: Re-use `CandleDataService` and `CandleReadDao` for fetching historical daily candles. 
- **Backtest Engine**: Create a dedicated Kotlin engine/service to process the CSV rows concurrently, fetch daily candles, and simulate the execution rules.
- **API & UI**: Build a new Next.js page with a CSV uploader, configuration inputs (Strategy type, Target %, SL %), a detailed Ant Design table for trades, and a summary section for monthly aggregations.

## Out of Scope
- Intraday (15-minute) tick-by-tick simulation (sticking to Daily OHLC).
- Short selling backtests (long only for now).
- Portfolio position sizing/capital allocation (tracking raw percentages only).

## Research Note: Post-Breakout Retests (2026-07-20)

An upside breakout that returns toward the broken resistance before continuing is a documented technical-analysis behaviour, usually called a **throwback** or **breakout retest**. Published pattern research supports the broad phenomenon, but does not establish a universal daily-stock rule of a 5-7% dip within exactly 2-3 sessions. That observation must be tested on the tool's own NSE signals and breakout definition.

The next backtest increment should compare entry policies rather than hard-code the observed dip:

1. Existing immediate entry: next available daily open after the signal.
2. Retest entry: wait up to a configurable 3-5 sessions for price to touch a breakout-level zone, then enter at the attainable daily-candle price (open if already below the limit; otherwise the limit).
3. Confirmed retest: after a zone touch, require a close back above the breakout level or a configurable confirmation condition, then enter at the following open.

For every breakout, record maximum adverse excursion in the first 2, 3, and 5 sessions, whether the breakout zone was retested, the retest depth (percent and ATR), retest volume versus breakout-day volume, and subsequent 20/60-day outcome. Segment results by market-cap bucket, base length, breakout gap, volume/delivery behaviour, and market regime. A retest must be treated as valid only while the close remains above a predeclared invalidation level; a raw 5-7% drawdown is not automatically constructive.

Use fixed parameter grids chosen before analysis, include missed breakouts that never retest, and reserve an out-of-sample period. This keeps the tool from converting a memorable observation into a curve-fit rule.

## Implemented: Retest Entries and Target + Trailing Stop (2026-07-20)

The CSV backtest now offers next-day-open, breakout-retest, and confirmed-retest entries. Retest modes derive the breakout level as the highest high in the 20 sessions before the signal, then wait for a configurable 1-20 session retest window. The zone defaults to 1% above that level. A direct retest fills at the daily open when already below the limit; otherwise it fills at the limit. A confirmed retest enters at the next open after a retest candle closes back at or above the breakout level.

Both fixed and trailing-stop strategies now require a target. In target + trailing-stop mode, the target ends the trade as soon as it is reached; the trailing stop protects only trades that have not reached the target. The trailing stop is updated from a completed candle's close only after that candle's open, low, and high have been evaluated, avoiding an impossible same-candle trailing-stop fill. Same-candle target/stop collisions remain conservative: the stop wins.

Validation: focused entry/exit evaluator tests (8) passed; the Resources compile and frontend production build passed.

## Implemented: First-Five-Session Dip (2026-07-20)

Every entered trade now records the lowest daily low from the entry session through the next four trading sessions, plus the rupee and percentage difference from the entry price. Monthly summaries include the average of that percentage. The metric intentionally continues through all five sessions even if the simulated target or stop exits earlier, so it measures post-signal price behaviour rather than only an open trade's path.

## Complexity Estimate
Medium-High. Involves a new UI page, new backend endpoints, a state machine for the backtest engine (handling gaps, conservative exits, trailing logic), and integrating the CSV parser.
