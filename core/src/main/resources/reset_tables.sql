-- =============================================================
-- TradingTool-3 — Full Reset
-- Drops all tables and recreates from scratch.
-- Run this in the Supabase SQL editor to wipe and start clean.
-- =============================================================

DROP TABLE IF EXISTS public.trades       CASCADE;
DROP TABLE IF EXISTS public.kite_tokens  CASCADE;
DROP TABLE IF EXISTS public.stocks       CASCADE;

\i tables.sql
