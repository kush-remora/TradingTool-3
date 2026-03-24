-- Remora Strategy: stores one row per stock per day a signal fires.
-- Only inserted when consecutiveDays >= 2, so this is a sparse audit log.
--
-- Unique constraint on (stock_id, signal_date) prevents duplicates if
-- the cron runs more than once on the same day.

CREATE TABLE remora_signals (
    id               SERIAL PRIMARY KEY,
    stock_id         INTEGER      NOT NULL,
    symbol           TEXT         NOT NULL,
    company_name     TEXT         NOT NULL,
    exchange         TEXT         NOT NULL,
    signal_type      TEXT         NOT NULL CHECK (signal_type IN ('ACCUMULATION', 'DISTRIBUTION')),
    volume_ratio     NUMERIC(6,3) NOT NULL,
    price_change_pct NUMERIC(8,4) NOT NULL,
    consecutive_days INTEGER      NOT NULL,
    signal_date      DATE         NOT NULL,
    computed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_remora_signals_stock_date ON remora_signals (stock_id, signal_date);
CREATE INDEX idx_remora_signals_signal_date ON remora_signals (signal_date DESC);
CREATE INDEX idx_remora_signals_signal_type ON remora_signals (signal_type);
