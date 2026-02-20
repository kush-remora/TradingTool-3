-- =================================================================
-- indexes.sql
--
-- Description: All indexes are now defined in tables.sql
-- This file contains optional additional performance indexes
-- =================================================================

-- NOTE: Core indexes are defined in tables.sql:
--   - idx_stocks_symbol
--   - idx_stocks_instrument_token
--   - idx_watchlist_stocks_watchlist_id
--   - idx_watchlist_stocks_stock_id
--   - idx_tags_name
--   - idx_stock_tags_stock_id
--   - idx_stock_tags_tag_id
--   - idx_watchlist_tags_watchlist_id
--   - idx_watchlist_tags_tag_id

-- Optional: Index for recent additions to watchlists
CREATE INDEX IF NOT EXISTS idx_watchlist_stocks_created_at
    ON public.watchlist_stocks(created_at DESC);

-- Optional: Index for recent stock additions
CREATE INDEX IF NOT EXISTS idx_stocks_created_at
    ON public.stocks(created_at DESC);

-- Optional: Composite index for filtering stocks by exchange and symbol
CREATE INDEX IF NOT EXISTS idx_stocks_exchange_symbol
    ON public.stocks(exchange, symbol);
