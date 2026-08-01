# Kite Candle Cache-Aside

Daily and intraday candles should no longer use Supabase as a persistence or fallback layer. Every candle consumer will read through one `CandleCacheService`: return the exact-range Redis entry when present, otherwise fetch the range from Kite and cache it for three hours. Redis TTL is the only invalidation mechanism; workflows must not independently synchronize candles or invalidate caches.

The implementation should preserve existing strategy behavior while replacing direct candle DAOs and duplicated stale-data checks. Supabase remains in use for non-candle application data. The scheduled candle refresh workflow becomes unnecessary and will be removed. Validation must cover cache hit, cache miss, three-hour TTL, daily and intraday Kite fetching, compilation of all affected modules, and focused tests.

Implemented across all candle consumers. Candle persistence and the refresh workflow were removed; Redis now holds exact-range results for 10,800 seconds and falls back directly to Kite on misses or cache failures. Full validation results are recorded in the feature journal.
