# Index Constituents as Stock Source of Truth

`public.index_constituents` will be the only maintained stock-universe table. Index syncs keep their existing `index_key` memberships, while Groww watchlist imports use the canonical `groww` key. Existing `public.stocks` data can be discarded, so the migration drops the legacy table without copying its rows.

The implementation removes `stocks` schema/constants and the obsolete `stock_id` link from delivery rows, routes WATCHLIST consumers and ticker startup to active `index_constituents` rows, and removes unused stock CRUD frontend/model remnants. Groww imports now replace the active `groww` membership and reject empty parsed inputs to avoid accidental mass deactivation. Core/resources tests, all affected-module compilation, and the frontend production build passed; the unrelated pre-existing stale-token assertion in `KiteStartupTokenValidationTest` still prevents a clean repository-wide `mvn test`.
