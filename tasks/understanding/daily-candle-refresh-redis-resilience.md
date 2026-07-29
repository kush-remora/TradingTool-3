# Daily Candle Refresh Redis Resilience

The GitHub Actions daily candle refresh completes its Supabase/Kite ingestion, then fails while invalidating Redis keys. GitHub-hosted runners cannot reach the configured Redis endpoint, making cache invalidation an unreliable cross-environment dependency. Redis is only a one-hour candle cache; it must not determine whether the authoritative database refresh succeeds.

The job will keep the database refresh as its required outcome and treat Redis invalidation as best-effort. A connection failure will be logged as a warning, while the workflow exits successfully after the candle data is persisted. Validation will compile the cron job through the Maven reactor.

Validation: `mvn -pl cron-job -am test -DskipTests --no-transfer-progress` passed for all seven Maven modules. The existing compiler warnings are unrelated to this change.

Review: the Kotlin and general code review found no blocking issues. The cache clear remains the same operation when Redis is reachable; only its failure handling changed.
