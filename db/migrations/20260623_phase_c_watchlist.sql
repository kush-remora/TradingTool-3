CREATE TABLE IF NOT EXISTS phase_c_watchlist (
    symbol TEXT PRIMARY KEY,
    instrument_token INTEGER,
    added_on DATE NOT NULL,
    last_seen_on DATE NOT NULL,
    status TEXT NOT NULL DEFAULT 'chartinkFilter', -- 'chartinkFilter', 'BREAKOUT_TRIGGERED', 'EXPIRED'
    stock_name TEXT,
    marketcapname TEXT,
    close_price REAL,
    pct_change TEXT,
    volume INTEGER,
    sector TEXT,
    industry TEXT,
    roce REAL,
    ronw REAL,
    net_profit_3q_ago REAL,
    debt_equity REAL,
    vol_dry_200_min INTEGER,
    vol_dry_60_min INTEGER,
    vol_dry_200_min_1_05 INTEGER,
    vol_dry_60_min_1_05 INTEGER,
    atr_count INTEGER
);
