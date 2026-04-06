# Watchlist Schema (V1)

This project uses a minimal schema for v1:

1. `stocks`
2. `watchlists`
3. `watchlist_stocks`

## Why this shape

- Covers core use cases immediately: store stocks, create watchlists, attach stocks to lists.
- Keeps queries simple and migration risk low.
- Avoids premature complexity from many-to-many tag normalization.

`stocks.tags` is a `TEXT[]` in v1 for fast iteration.
If tag governance becomes complex (shared taxonomy, categories, analytics), evolve to v2 tables:
- `tags`
- `stock_tags`

## Files

- `src/infrastructure/database/tables.sql`
- `src/infrastructure/database/indexes.sql`

## Apply in Supabase SQL editor

Run in this order:

1. `tables.sql`
2. `indexes.sql`

## Core query examples

Add stock to watchlist:

```sql
INSERT INTO public.watchlist_stocks (watchlist_id, stock_id, notes)
VALUES (1, 101, 'Breakout candidate')
ON CONFLICT (watchlist_id, stock_id) DO NOTHING;
```

Get all stocks in one watchlist:

```sql
SELECT s.id, s.nse_symbol, s.company_name, ws.notes, ws.created_at
FROM public.watchlist_stocks ws
JOIN public.stocks s ON s.id = ws.stock_id
WHERE ws.watchlist_id = 1
ORDER BY ws.created_at DESC;
```

Filter stocks by tag:

```sql
SELECT id, nse_symbol, company_name
FROM public.stocks
WHERE 'momentum' = ANY(tags)
ORDER BY nse_symbol;
```
