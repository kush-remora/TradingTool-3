-- =================================================================
-- indexes.sql
--
-- Description: Extra indexes for watchlist feature queries.
-- Note: PK + UNIQUE indexes are auto-created by PostgreSQL.
-- =================================================================

-- Find all watchlists that include a given stock.
CREATE INDEX IF NOT EXISTS idx_watchlist_stocks_stock_id
    ON public.watchlist_stocks(stock_id);

-- Fast filter for stocks by tag values in v1 (tags is TEXT[]).
CREATE INDEX IF NOT EXISTS idx_stocks_tags_gin
    ON public.stocks USING GIN(tags);

-- Optional read optimization for recent additions to watchlists.
CREATE INDEX IF NOT EXISTS idx_watchlist_stocks_created_at
    ON public.watchlist_stocks(created_at DESC);

-- Fast lookups for tags assigned to stocks/watchlists via shared link table.
CREATE INDEX IF NOT EXISTS idx_tag_links_tag_id
    ON public.tag_links(tag_id);

CREATE INDEX IF NOT EXISTS idx_tag_links_stock_id
    ON public.tag_links(stock_id)
    WHERE stock_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tag_links_watchlist_id
    ON public.tag_links(watchlist_id)
    WHERE watchlist_id IS NOT NULL;
