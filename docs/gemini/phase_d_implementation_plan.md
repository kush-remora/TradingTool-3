# Phase D Ignition Scanner Implementation Plan

## User Review Required
> [!IMPORTANT]
> **SQL Table Design:** To make the system robust, I propose a `phase_c_watchlist` table that acts as a state machine. It will track when a stock started coiling, when we last saw it on Chartink, and if it has broken out. Does this schema look good to you?
> 
> **CSV Ingestion Job:** Do you want a simple UI page to upload the `.csv` file daily, or an API endpoint you hit via Postman/Curl? (A UI page is usually fastest for daily use).

## Proposed Architecture

### 1. Database Layer
#### [NEW] SQL Migration `db/migrations/20260623_phase_c_watchlist.sql`
Create the `phase_c_watchlist` table to store the state of our coiled stocks:

```sql
CREATE TABLE IF NOT EXISTS phase_c_watchlist (
    symbol TEXT PRIMARY KEY,
    instrument_token INTEGER,          -- From Kite instrument resolution
    added_on DATE NOT NULL,            -- First day it appeared in Chartink export
    last_seen_on DATE NOT NULL,        -- Last day it was in the Chartink export
    status TEXT NOT NULL DEFAULT 'chartinkFilter', -- 'chartinkFilter', 'BREAKOUT_TRIGGERED', 'EXPIRED'
    
    -- Chartink Export Columns
    market_cap_bucket TEXT,
    close_price REAL,
    pct_change TEXT,
    volume INTEGER,
    sector TEXT,
    industry TEXT,
    roce_pct REAL,                     -- Yearly Return on capital employed %
    ronw_pct REAL,                     -- Yearly Return on net worth %
    net_profit_after_tax REAL,         -- Quarterly reported profit after tax
    debt_equity_ratio REAL,            -- Yearly Debt equity ratio
    atr_lt_2pct_count INTEGER          -- count daily atr < 2 %
);
```

### 2. Backend Ingestion Job
#### [NEW] `PhaseCWatchlistService.kt` & `PhaseCWatchlistJdbiHandler.kt`
- Create a service that parses the uploaded `.tsv` or `.csv` from Chartink.
- **Upsert Logic:** 
  - If `symbol` is new: Insert with `status = 'chartinkFilter'` and `added_on = TODAY`.
  - If `symbol` exists: Update `last_seen_on = TODAY`.
  - If a stock in the DB hasn't been `last_seen_on` for 14 days, update its `status = 'EXPIRED'` (the coil failed or took too long).

### 3. The Phase D Scanner
#### [NEW] `PhaseDScannerService.kt`
- Runs daily (either via cron or a UI button).
- Selects all symbols from `phase_c_watchlist` where `status = 'chartinkFilter'`.
- Fetches their latest EOD candle and delivery data.
- **Trigger Logic:** Checks if today's volume > `5.0x` (configurable) the 10-day average, and price broke out.
- If it triggers, it updates the DB `status = 'BREAKOUT_TRIGGERED'`.

### 4. Frontend UI
#### [NEW] `frontend/src/pages/PhaseDScannerPage.tsx`
- A dashboard with two tabs:
  1. **Upload Phase C:** A dropzone or file uploader to dump the Chartink `.csv`.
  2. **Phase D Dashboard:** A table showing all stocks currently marked as `chartinkFilter`, and high-lighting those that just fired a `BREAKOUT_TRIGGERED` alert today.

## Verification Plan
1. Apply the SQL migration and verify table creation.
2. Build the CSV parsing API and upload the dummy data you just provided.
3. Query the SQLite database directly to confirm `added_on` and `status` are correctly initialized.
