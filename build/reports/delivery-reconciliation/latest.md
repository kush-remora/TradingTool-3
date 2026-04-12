# Delivery Reconciliation Report

- Requested date: `2026-04-10`
- Resolved date: `2026-04-10`
- Expected symbols: `502`
- Present rows: `501`
- Missing from source: `0`
- Nullable stock_id rows: `482`
- Watchlist-linked rows: `19`
- Non-watchlist rows: `482`
- Fetched from source: `false`
- Already complete: `true`

## Samples

### Present

| Symbol | Token | stock_id | Status | Delivery % |
|---|---:|---:|---|---:|
| `AARTIIND` | `1793` | `-` | `PRESENT` | `64.51` |
| `ABB` | `3329` | `-` | `PRESENT` | `48.75` |
| `ACC` | `5633` | `-` | `PRESENT` | `42.1` |
| `ADANIENT` | `6401` | `-` | `PRESENT` | `33.04` |
| `AEGISLOG` | `10241` | `-` | `PRESENT` | `14.73` |

### Missing From Source

_None_

### Nullable stock_id

| Symbol | Token | stock_id | Status | Delivery % |
|---|---:|---:|---|---:|
| `AARTIIND` | `1793` | `-` | `PRESENT` | `64.51` |
| `ABB` | `3329` | `-` | `PRESENT` | `48.75` |
| `ACC` | `5633` | `-` | `PRESENT` | `42.1` |
| `ADANIENT` | `6401` | `-` | `PRESENT` | `33.04` |
| `AEGISLOG` | `10241` | `-` | `PRESENT` | `14.73` |

## Unresolved Issues

- Instrument token could not be resolved for configured symbol SCHNEIDER.
- Expected 502 reconciled rows but found 501 rows in stock_delivery_daily.
