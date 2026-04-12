# RSI Momentum Calibration Report

- Run at: `2026-04-11T19:05:58.198927Z`
- Method: `RISK_ADJUSTED_SORTINO_WITH_GUARDRAILS_V1`
- Sample range: `2021-04-11..2026-04-11`
- Transaction cost: `10.0 bps`

## LargeMidcap250 (`largemidcap250`)
- Universe: `NIFTY_LARGEMIDCAP_250`
- Selected RSI periods: `22/44/66`
- Selection reason: No stable candidate passed guardrails; chose highest Sortino candidate.

| RSI Periods | Sortino | Sharpe | CAGR % | Max DD % | Turnover % | Stable | Rejection |
|---|---:|---:|---:|---:|---:|---|---|
| `22/44/66` | 0.99 | 0.68 | 13.79 | 29.09 | 20.77 | NO | UNSTABLE_SPLIT_SORTINO |
| `30/60/90` | 0.84 | 0.6 | 11.83 | 29.69 | 15.0 | NO | UNSTABLE_SPLIT_SORTINO |
| `21/42/84` | 0.84 | 0.59 | 10.72 | 26.69 | 20.38 | NO | UNSTABLE_SPLIT_SORTINO |
| `20/40/60` | 0.71 | 0.48 | 8.54 | 25.63 | 23.85 | NO | UNSTABLE_SPLIT_SORTINO |


## Smallcap250 (`smallcap250`)
- Universe: `NIFTY_SMALLCAP_250`
- Selected RSI periods: `63/126/252`
- Selection reason: No stable candidate passed guardrails; chose highest Sortino candidate.

| RSI Periods | Sortino | Sharpe | CAGR % | Max DD % | Turnover % | Stable | Rejection |
|---|---:|---:|---:|---:|---:|---|---|
| `63/126/252` | 2.05 | 1.12 | 17.31 | 16.04 | 1.35 | NO | UNSTABLE_SPLIT_SORTINO |
| `21/63/252` | 1.54 | 0.91 | 13.24 | 15.61 | 1.35 | NO | UNSTABLE_SPLIT_SORTINO |
| `22/44/66` | 1.29 | 0.84 | 19.66 | 29.09 | 0.96 | NO | UNSTABLE_SPLIT_SORTINO |
| `30/60/90` | 1.01 | 0.69 | 15.26 | 29.69 | 0.96 | NO | UNSTABLE_SPLIT_SORTINO |

