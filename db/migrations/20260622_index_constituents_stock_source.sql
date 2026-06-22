BEGIN;

CREATE TABLE IF NOT EXISTS public.index_constituents (
    index_key        TEXT        NOT NULL,
    symbol           TEXT        NOT NULL,
    instrument_token BIGINT      NOT NULL,
    company_name     TEXT        NOT NULL DEFAULT '',
    industry         TEXT        NOT NULL DEFAULT '',
    series           TEXT        NOT NULL DEFAULT '',
    isin_code        TEXT        NOT NULL DEFAULT '',
    is_active        BOOLEAN     NOT NULL DEFAULT true,
    source_url       TEXT        NOT NULL,
    last_synced_at   TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (index_key, symbol)
);

ALTER TABLE IF EXISTS public.stock_delivery_daily
    DROP COLUMN IF EXISTS stock_id CASCADE;

DROP TABLE IF EXISTS public.stocks;

CREATE INDEX IF NOT EXISTS idx_index_constituents_symbol
    ON public.index_constituents (symbol);
CREATE INDEX IF NOT EXISTS idx_index_constituents_instrument_token
    ON public.index_constituents (instrument_token);
CREATE INDEX IF NOT EXISTS idx_index_constituents_index_active
    ON public.index_constituents (index_key, is_active);

COMMIT;
