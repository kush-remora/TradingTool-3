# Daily Top-Volume Ingestion — Living Understanding

The immediate requirement replaces the shortlist-first decision in `docs/features/composite-volume-pipeline/2026-06-20/daily-volume-router-requirement.md`. We do not yet know how the data will be used, so the first version must ingest and retain all 100 stocks from each daily top-volume dataset in a dedicated SQL table. Ranking, enrichment, Wyckoff interpretation, classification, UI, and trading decisions are deferred.

The ingestion should be a manually invoked Kotlin job modeled operationally on `GrowwWatchlistSyncJob.kt`. Its input is a manually copied JSON file. Each file represents exactly one daily snapshot, and rerunning a date must atomically replace all previously stored rows for that date. Historical dates remain retained.

The model uses one typed row per trading date, exchange, and symbol, including source rank and ingestion metadata. The required `trade_date` is supplied explicitly when invoking the job so delayed or corrected imports cannot be silently mislabeled.

## Source Inspection and Coverage Decision

The updated `manual-input/groww_volume_shocker` is the Groww browser/API `top_movers` response containing exactly 100 rows ordered descending by `volume / volumeWeekAvg`. Rank 100 is still approximately a `3.02x` weekly-volume shock. A direct Postman request can return more rows, but its company and exchange identity fields are empty; the response used by Groww contains complete identity data for the top 100. V1 should ingest these 100 self-contained rows rather than introduce a second enrichment workflow for another 400 rows. We can revisit coverage after collecting evidence.

The populated source fields are `isin`, `gsin`, `companyName`, `companyShortName`, `searchId`, `logoUrl`, `nseScriptCode`, `bseScriptCode`, `type`, `marketCap`, `volumeWeekAvg`, `close`, `yearLow`, `yearHigh`, `volume`, `tag`, and `tagColor`; rank comes from array position. NSE code is present for 94 rows, while six rows are BSE-only. The stored identity is the preferred NSE symbol, or the BSE scrip code when NSE is absent. Groww IDs and presentation metadata are validated where needed but not persisted.

## Proposed Database Model

Use one source-specific table, `groww_volume_shocker_daily`, with one row per exchange symbol per trading date. SQL uses only column types, `NOT NULL`, timestamps/defaults, and a generated `id` as its technical primary key. All domain rules—including allowed exchanges, rank bounds, positive values, market-cap categories, year-high/low consistency, and uniqueness—are enforced by the ingestion service before any database write. The table has no business `CHECK` or `UNIQUE` constraints.

After review, the model is intentionally limited to fields needed to identify the stock and later analyze why it appeared in the volume-shocker list: `trade_date`, `source_rank`, `exchange`, `symbol`, `instrument_token`, `company_name`, `ltp`, `close`, `market_cap_crore`, `market_cap_category`, `year_low`, `year_high`, `volume`, `weekly_average_volume`, and `ingested_at`. For dual-listed stocks, prefer the NSE symbol; when NSE is absent, store the BSE scrip code and `exchange = BSE`. `instrument_token` is resolved through the existing Kite resolver and is mandatory; an unresolved token fails the complete import.

Do not store Groww ID, ISIN, short name, search ID, logo, tags, raw JSON, `price_change_pct`, or `volume_shock_ratio`. The two calculated values can be derived from stored inputs when downstream usage is decided. The job should take an explicit `trade_date`, validate 1-100 unique ranked rows, and replace that date's rows in one database transaction.

`market_cap_category` cannot honestly represent the official Indian large/mid/small classification from `market_cap_crore` alone. SEBI/AMFI classification is based on each company's rank across the whole market: ranks 1-100 are large cap, 101-250 mid cap, and 251 onward small cap. For this feature, use a deliberately approximate TradingTool classification based on the Groww market-cap value: `LARGE` at or above ₹105,000 crore, `MID` at or above ₹34,700 crore and below ₹105,000 crore, and `SMALL` below ₹34,700 crore. These thresholds provide rough context only and must not be presented as an official or permanently current classification.

## Delivery Process

Kush has requested an interview-first workflow: continue clarifying until confidence reaches at least 95%, then implement without another planning handoff. Implementation must include the SQL migration, typed Kotlin ingestion path, manually invoked cron job modeled on `GrowwWatchlistSyncJob`, atomic same-date replacement, tests, relevant build verification, code review, and the required feature journal update.

## Confirmed Job Contract and API Feasibility

The job reads the fixed `manual-input/groww_volume_shocker` file and requires an explicit `YYYY-MM-DD` trade-date argument. `instrument_token` is `NOT NULL`; the import must resolve every row through Kite before changing database state, and any unresolved token fails the complete run.

A direct unauthenticated GET to Groww's `top_movers` endpoint succeeds and returns all 100 price/volume rows, but `isin`, company names, search ID, NSE code, and BSE code are empty for every row. Supplying normal browser-style `Origin`, `Referer`, `User-Agent`, and `x-app-id` headers does not populate them. Because symbols are absent, the direct response cannot satisfy mandatory instrument-token resolution. V1 therefore remains manual-file ingestion using the complete JSON copied from Groww's own browser request; direct API automation is out of scope.

## Implementation Outcome

The table migration, canonical `tables.sql` definition, strict file source, typed ingestion service, transactional JDBI gateway, and manual cron job are implemented. Validation occurs before database replacement: the input must contain exactly 100 complete unique rows, all instrument tokens must resolve, and resolved tokens must also be unique. The existing Kite resolver now maps BSE-only Groww scrip codes through Kite exchange tokens; all six BSE-only rows in the inspected input exist in Kite's instrument dump. Focused tests pass and the full Maven dependency chain through `cron-job` compiles. The unrelated existing Groww watchlist default/test mismatch remains untouched.
