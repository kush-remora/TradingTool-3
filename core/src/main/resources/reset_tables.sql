-- =============================================================
-- TradingTool-3 — Full Reset
-- Drops all tables and recreates from scratch.
-- Run this in the Supabase SQL editor to wipe and start clean.
-- =============================================================

DROP TABLE IF EXISTS public.remora_signals CASCADE;
DROP TABLE IF EXISTS public.stock_delivery_daily CASCADE;
DROP TABLE IF EXISTS public.intraday_candles CASCADE;
DROP TABLE IF EXISTS public.daily_candles CASCADE;
DROP TABLE IF EXISTS public.trades CASCADE;
DROP TABLE IF EXISTS public.stock_indicators_snapshot CASCADE;
DROP TABLE IF EXISTS public.kite_tokens CASCADE;
DROP TABLE IF EXISTS public.stocks CASCADE;

\i tables.sql
