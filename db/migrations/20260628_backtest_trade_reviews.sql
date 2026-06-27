-- Create the backtest trade reviews table
CREATE TABLE IF NOT EXISTS backtest_trade_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol VARCHAR(50) NOT NULL,
    signal_date DATE NOT NULL,
    market_cap VARCHAR(100),
    sector VARCHAR(100),
    entry_date DATE,
    entry_price NUMERIC,
    exit_date DATE,
    exit_price NUMERIC,
    pnl_pct NUMERIC,
    days_held INT,
    sl_hit BOOLEAN,
    is_pass BOOLEAN,
    reason_tags VARCHAR(500),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_backtest_trade_reviews_symbol_date UNIQUE(symbol, signal_date)
);

-- Index for querying by symbol or pass/reject status
CREATE INDEX idx_backtest_trade_reviews_symbol ON backtest_trade_reviews(symbol);
CREATE INDEX idx_backtest_trade_reviews_status ON backtest_trade_reviews(is_pass);
