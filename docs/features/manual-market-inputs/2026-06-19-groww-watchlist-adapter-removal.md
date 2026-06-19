# Groww Watchlist Sync Cleanup

## Feature name and why it was built
Groww Watchlist Sync Cleanup. This was built to keep the watchlist sync implementation honest: the live workflow uses manual JSON input, not a direct Groww watchlist HTTP call, so it should not carry a fake watchlist ID either.

## What was implemented
1. Removed the unused `GrowwWatchlistAdapter` Kotlin class that called the Groww watchlist endpoint.
2. Removed the dedicated adapter unit test that only covered that deleted HTTP integration path.
3. Removed `watchlistId` from the sync request/result model and deleted `DEFAULT_WATCHLIST_ID` from the cron runtime.
4. Updated the cron log/report path and the upload parser path to use file/upload wording instead of a pretend Groww watchlist ID.

## Key decisions and tradeoffs
1. Chose deletion instead of keeping a dormant fallback path because the project already prefers manual file-based ingest for reliability.
2. Removed the model field entirely instead of keeping an optional label because the current code paths do not need it and YAGNI fits better here.
3. Kept the remaining file and string adapters intact, only trimming the now-unused request metadata.

## Validation run and outcomes
1. `rg -n "GrowwWatchlistAdapter|v1/api/presentation/v2/watchlist|include_index_fno=true" . -S` confirmed the removed adapter and endpoint no longer appear in runtime code.
2. `mvn -q -pl core -Dtest=GrowwWatchlistSyncServiceTest,KiteInstrumentTokenResolverTest test` passed after the deletion.
3. `rg -n "watchlistId|DEFAULT_WATCHLIST_ID|GWL_" cron-job core resources -S` confirmed the fake watchlist-ID plumbing is gone from active source files.
4. `mvn -q -pl cron-job -am -DskipTests compile` passed with dependent modules rebuilt from source.

## Next follow-ups
1. If Groww watchlist ingestion ever needs to become network-based again, reintroduce it as a fresh, explicitly approved integration instead of reviving stale dead code.
