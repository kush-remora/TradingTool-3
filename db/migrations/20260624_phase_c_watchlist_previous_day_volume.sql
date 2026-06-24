ALTER TABLE IF EXISTS public.phase_c_watchlist
    ADD COLUMN IF NOT EXISTS previous_day_volume BIGINT;
