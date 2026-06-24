DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'marketcapname'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN marketcapname TO market_cap_bucket;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'roce'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN roce TO roce_pct;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'ronw'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN ronw TO ronw_pct;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'net_profit_3q_ago'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN net_profit_3q_ago TO net_profit_after_tax;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'debt_equity'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN debt_equity TO debt_equity_ratio;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'vol_dry_200_min'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN vol_dry_200_min TO vol_dry_200d_min_count;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'vol_dry_60_min'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN vol_dry_60_min TO vol_dry_60d_min_count;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'vol_dry_200_min_1_05'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN vol_dry_200_min_1_05 TO vol_dry_200d_min_105_count;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'vol_dry_60_min_1_05'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN vol_dry_60_min_1_05 TO vol_dry_60d_min_105_count;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'promoter_holding'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN promoter_holding TO indian_promoter_pct;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'foreign_promoter_holding'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN foreign_promoter_holding TO foreign_promoter_pct;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'gross_sales'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN gross_sales TO quarterly_gross_sales;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'high_252d'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN high_252d TO high_52w;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'min_20d_high'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN min_20d_high TO low_52w;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'dist_200d_high'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN dist_200d_high TO dist_200d_high_pct;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'brackets2'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN brackets2 TO dist_200d_low_pct;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'phase_c_watchlist'
          AND column_name = 'atr_count'
    ) THEN
        ALTER TABLE public.phase_c_watchlist RENAME COLUMN atr_count TO atr_lt_2pct_count;
    END IF;
END $$;
