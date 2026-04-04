CREATE TABLE stock_delivery_daily (
    stock_id INTEGER NOT NULL REFERENCES stocks(id),
    symbol TEXT NOT NULL,
    exchange TEXT NOT NULL,
    trading_date DATE NOT NULL,
    series TEXT,
    ttl_trd_qnty BIGINT,
    deliv_qty BIGINT,
    deliv_per DECIMAL(10, 2),
    source_file_name TEXT,
    source_url TEXT,
    fetched_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stock_id, trading_date)
);

CREATE INDEX idx_stock_delivery_daily_trading_date ON stock_delivery_daily (trading_date DESC);
CREATE INDEX idx_stock_delivery_daily_symbol_date ON stock_delivery_daily (symbol, trading_date DESC);
