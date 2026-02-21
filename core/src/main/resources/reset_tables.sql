-- =============================================================
-- TradingTool-3 — Full Reset
-- Drops all tables and recreates them from scratch.
-- Run this in Supabase SQL editor to wipe and start clean.
-- =============================================================

-- Drop in reverse dependency order so foreign keys don't block drops
DROP TABLE IF EXISTS public.kite_tokens       CASCADE;
DROP TABLE IF EXISTS public.user_layout       CASCADE;
DROP TABLE IF EXISTS public.stock_notes       CASCADE;
DROP TABLE IF EXISTS public.watchlist_tags    CASCADE;
DROP TABLE IF EXISTS public.stock_tags        CASCADE;
DROP TABLE IF EXISTS public.tags              CASCADE;
DROP TABLE IF EXISTS public.watchlist_stocks  CASCADE;
DROP TABLE IF EXISTS public.watchlists        CASCADE;
DROP TABLE IF EXISTS public.stocks            CASCADE;

-- Recreate everything (tables.sql now includes all indexes)
\i tables.sql
