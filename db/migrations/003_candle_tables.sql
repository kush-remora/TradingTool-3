-- Migration 003: Raw OHLCV candle storage
-- daily_candles  → one row per (instrument, date)     — for long-term metrics (SMA200, momentum, etc.)
-- intraday_candles → one row per (instrument, interval, timestamp) — for intraday patterns

CREATE TABLE IF NOT EXISTS daily_candles (
    instrument_token  BIGINT         NOT NULL,
    symbol            TEXT           NOT NULL,
    candle_date       DATE           NOT NULL,
    open              NUMERIC(12, 4) NOT NULL,
    high              NUMERIC(12, 4) NOT NULL,
    low               NUMERIC(12, 4) NOT NULL,
    close             NUMERIC(12, 4) NOT NULL,
    volume            BIGINT         NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (instrument_token, candle_date)
);

-- interval is stored explicitly (e.g. '15minute') so a second interval (e.g. '5minute') can
-- be added later without a schema change — just insert rows with a different interval value.
CREATE TABLE IF NOT EXISTS intraday_candles (
    instrument_token  BIGINT         NOT NULL,
    symbol            TEXT           NOT NULL,
    interval          TEXT           NOT NULL,
    candle_timestamp  TIMESTAMP      NOT NULL, -- always IST, no timezone (Kite always returns IST)
    open              NUMERIC(12, 4) NOT NULL,
    high              NUMERIC(12, 4) NOT NULL,
    low               NUMERIC(12, 4) NOT NULL,
    close             NUMERIC(12, 4) NOT NULL,
    volume            BIGINT         NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (instrument_token, interval, candle_timestamp)
);

CREATE INDEX IF NOT EXISTS idx_daily_candles_symbol      ON daily_candles (symbol, candle_date);
CREATE INDEX IF NOT EXISTS idx_intraday_candles_symbol   ON intraday_candles (symbol, interval, candle_timestamp);
