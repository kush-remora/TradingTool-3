ALTER TABLE IF EXISTS public.phase_c_watchlist
    ADD COLUMN IF NOT EXISTS phase_2_delivery_status TEXT NOT NULL DEFAULT 'NOT_RUN',
    ADD COLUMN IF NOT EXISTS phase_2_reason TEXT,
    ADD COLUMN IF NOT EXISTS phase_2_evaluated_on DATE,
    ADD COLUMN IF NOT EXISTS delivery_quantity_today BIGINT,
    ADD COLUMN IF NOT EXISTS delivery_pct_today REAL,
    ADD COLUMN IF NOT EXISTS wholesale_base_dq BIGINT,
    ADD COLUMN IF NOT EXISTS delivery_spike_ratio REAL,
    ADD COLUMN IF NOT EXISTS conviction_days_10d INTEGER,
    ADD COLUMN IF NOT EXISTS conviction_days_20d INTEGER;
