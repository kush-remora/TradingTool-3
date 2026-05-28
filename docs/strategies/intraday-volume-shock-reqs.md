# Intraday Volume Shock (9:30 AM) - Requirements

## Overview
An intraday Wyckoff accumulation scanner that detects massive volume shocks in the early morning and capitalizes on the intraday momentum.

## Core Rules

1. **Data Scope**: 
   - 1 year (252 days) historical daily data only.

2. **Configurable Scanner Time (Default: 9:15 AM - 9:30 AM)**:
   - Volume accumulated during this window must be GREATER than at least one of:
     - Highest daily volume in the last 1 year (252 days)
     - Highest daily volume in the last 6 months (126 days)
     - Highest daily volume in the last quarter (63 days)
     - Highest daily volume in the last 60 days
   - *If true, the stock is in the "volume zone".*

3. **Safeguard (Gap Limit)**:
   - If the stock opens more than 5% (configurable) higher than yesterday's close, **SKIP**. The move is already priced in.

4. **Configurable Entry Strategy**:
   - Buy at the open of the candle at the specified entry time (Default: 9:45 AM).

5. **Exit Strategy**:
   - **Target Exit**: Exit at 5% profit. (A 5% move on top of a morning gap is an excellent intraday gain. In the future, we can add a trailing stop feature to let winners run to 10%+).
   - **Stop Loss**: Exit if the price drops below the **Low of the morning scan range** (e.g., lowest price from 9:15 to 9:30 AM) OR if it hits a hard **-3%** drop from the entry price (whichever is hit first). Institutions should defend the morning low; if they don't, it's a trap.
   - **Time Exit**: Exit strictly at 2:30 PM (End of trade window).

## Multi-Platform Compatibility
- **Kotlin Backend (Current Target)**: Used for running the backtest massively across the entire custom universe (500+ stocks) simultaneously and saving results to the database.
- **TradingView (Pine Script)**: This exact strategy is 100% compatible with Pine Script. Using `request.security()`, a Pine Script running on a 15-minute chart can pull the Daily volume data to calculate the 1-year/6-month maximums and execute the exact same gap, target, and stop rules visually. Pine Script can be used for rapid visual iteration if needed in the future.

---
*Status: Approved for Implementation*
