ALTER TABLE IF EXISTS public.phase_c_watchlist
    ADD COLUMN IF NOT EXISTS market_fields_updated_on DATE;
