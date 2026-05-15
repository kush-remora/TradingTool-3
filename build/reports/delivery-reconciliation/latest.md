# Delivery Reconciliation Report

- Requested date: `latest`
- Resolved date: `2026-05-15`
- Expected symbols: `526`
- Present rows: `521`
- Missing from source: `1`
- Nullable stock_id rows: `397`
- Watchlist-linked rows: `125`
- Non-watchlist rows: `397`
- Fetched from source: `false`
- Already complete: `true`
- Blocking issues: `0`
- Warnings: `2`

## Samples

### Present

| Symbol | Token | stock_id | Status | Delivery % |
|---|---:|---:|---|---:|
| `AARTIIND` | `1793` | `-` | `PRESENT` | `45.86` |
| `ABB` | `3329` | `-` | `PRESENT` | `34.41` |
| `ACC` | `5633` | `-` | `PRESENT` | `41.78` |
| `ADANIENT` | `6401` | `414` | `PRESENT` | `17.54` |
| `AEGISLOG` | `10241` | `-` | `PRESENT` | `31.63` |

### Missing From Source

| Symbol | Token | stock_id | Status | Delivery % |
|---|---:|---:|---|---:|
| `NIFTY 50` | `256265` | `747` | `MISSING_FROM_SOURCE` | `-` |

### Nullable stock_id

| Symbol | Token | stock_id | Status | Delivery % |
|---|---:|---:|---|---:|
| `AARTIIND` | `1793` | `-` | `PRESENT` | `45.86` |
| `ABB` | `3329` | `-` | `PRESENT` | `34.41` |
| `ACC` | `5633` | `-` | `PRESENT` | `41.78` |
| `AEGISLOG` | `10241` | `-` | `PRESENT` | `31.63` |
| `APOLLOHOSP` | `40193` | `-` | `PRESENT` | `64.06` |

## Warnings

- Ignoring 4 unresolved symbol(s) because availability gap is under 1% of the configured universe: AKZOINDIA, BHAGYANGR, GSPL, SCHNEIDER.
- Expected 526 reconciled rows but found 522 rows in stock_delivery_daily.
