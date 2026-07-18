ALTER TABLE public.accumulation_analysis_runs
    ADD COLUMN IF NOT EXISTS period_key TEXT,
    ADD COLUMN IF NOT EXISTS months INTEGER;

UPDATE public.accumulation_analysis_runs
SET period_key = CASE months
    WHEN 1 THEN 'ONE_MONTH'
    WHEN 3 THEN 'THREE_MONTHS'
    WHEN 6 THEN 'SIX_MONTHS'
    WHEN 9 THEN 'NINE_MONTHS'
    ELSE 'ONE_MONTH'
END
WHERE period_key IS NULL;

ALTER TABLE public.accumulation_analysis_runs
    ALTER COLUMN period_key SET NOT NULL,
    ALTER COLUMN months DROP NOT NULL;
