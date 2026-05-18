# Delivery Reconciliation Report

- Requested date: `latest`
- Resolved date: `2026-05-18`
- Expected symbols: `526`
- Present rows: `521`
- Missing from source: `1`
- Nullable stock_id rows: `396`
- Watchlist-linked rows: `126`
- Non-watchlist rows: `396`
- Fetched from source: `false`
- Already complete: `true`
- Blocking issues: `0`
- Warnings: `2`

## Samples

### Present

| Symbol | Token | stock_id | Status | Delivery % |
|---|---:|---:|---|---:|
| `AARTIIND` | `1793` | `-` | `PRESENT` | `45.19` |
| `ABB` | `3329` | `-` | `PRESENT` | `48.22` |
| `ACC` | `5633` | `-` | `PRESENT` | `55.48` |
| `ADANIENT` | `6401` | `414` | `PRESENT` | `20.01` |
| `AEGISLOG` | `10241` | `-` | `PRESENT` | `29.23` |

### Missing From Source

| Symbol | Token | stock_id | Status | Delivery % |
|---|---:|---:|---|---:|
| `NIFTY 50` | `256265` | `747` | `MISSING_FROM_SOURCE` | `-` |

### Nullable stock_id

| Symbol | Token | stock_id | Status | Delivery % |
|---|---:|---:|---|---:|
| `AARTIIND` | `1793` | `-` | `PRESENT` | `45.19` |
| `ABB` | `3329` | `-` | `PRESENT` | `48.22` |
| `ACC` | `5633` | `-` | `PRESENT` | `55.48` |
| `AEGISLOG` | `10241` | `-` | `PRESENT` | `29.23` |
| `APOLLOHOSP` | `40193` | `-` | `PRESENT` | `63.01` |

## Warnings

- Ignoring 4 unresolved symbol(s) because availability gap is under 1% of the configured universe: AKZOINDIA, BHAGYANGR, GSPL, SCHNEIDER.
- Expected 526 reconciled rows but found 522 rows in stock_delivery_daily.
