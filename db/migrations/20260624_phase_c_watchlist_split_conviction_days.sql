ALTER TABLE IF EXISTS public.phase_c_watchlist
    DROP COLUMN IF EXISTS conviction_days_10d,
    DROP COLUMN IF EXISTS conviction_days_20d;

ALTER TABLE IF EXISTS public.phase_c_watchlist
    ADD COLUMN IF NOT EXISTS delivery_spike_days_10d INTEGER,
    ADD COLUMN IF NOT EXISTS delivery_spike_days_20d INTEGER,
    ADD COLUMN IF NOT EXISTS delivery_support_days_10d INTEGER,
    ADD COLUMN IF NOT EXISTS delivery_support_days_20d INTEGER;
