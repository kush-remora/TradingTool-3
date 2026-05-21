# Feature Journey - Delivery Reconciliation Reliability Hardening (2026-05-21)

## Feature Name and Why
Delivery reconciliation cron reliability hardening.
We needed clearer failure behavior so missing data quality does not pass silently.

## What Was Implemented
- Added detailed HTTP error text in delivery source download failures.
- Added configurable backfill knobs:
  - `DELIVERY_BACKFILL_REQUIRED_TRADING_DAYS`
  - `DELIVERY_BACKFILL_PARALLEL_BATCH_SIZE`
  - `DELIVERY_BACKFILL_EXTRA_CANDIDATE_DAYS`
  - `DELIVERY_BACKFILL_MAX_FAILED_DATES`
- Added backfill outcome classification for source-unavailable dates (HTTP 404).
- Added strict backfill quality gates (coverage and failure threshold).
- Hardened CLI arg parsing (`--help`, unknown/malformed argument rejection).

## Key Decisions / Tradeoffs
- Treated HTTP 404 archive misses as unavailable dates to reduce false failure noise.
- Kept non-404 reconciliation failures as hard quality signals.
- Added configurable defaults to avoid hardcoding operational assumptions.

## Validation Run and Outcomes
- `mvn -q -pl core,cron-job -am -DskipTests compile` passed.
- `mvn -q -pl core test -Dtest=DeliveryReconciliationRunReportTest,DeliveryReconciliationAnalyzerTest,NseDeliverySourceAdapterTest` passed.

## Next Follow-ups
- If needed, add explicit NSE holiday-calendar integration to reduce archive probing.

## Follow-up Patch - Source Rule + Full Source Persistence

### Why
Shift from config-scoped universe reconciliation to full-source persistence, and adopt deterministic source routing by date.

### What Changed
- Reconciliation now persists all deduped source symbols for a date (token-resolved rows) instead of configured universe-only rows.
- Source routing rule added:
  - trading date is today/yesterday (IST): prefer `MTO`, fallback `CM_BHAVDATA_FULL`
  - older dates: prefer `CM_BHAVDATA_FULL`, fallback `MTO`
- Universe value written as `deprecated` for this flow.

### Validation
- `mvn -q -pl core,service,resources,cron-job -am -DskipTests compile` passed.
- `mvn -q -pl core test -Dtest=NseDeliverySourceAdapterTest,DeliveryReconciliationAnalyzerTest,DeliveryReconciliationRunReportTest,StockDeliveryMapperTest,DeliveryConfigServiceTest,DeliveryUniverseServiceTest` passed.
