# Three-Touch Base Retest Backtest

This experiment detects a horizontal support base from two distinct lows. After the first low, price must rally at least 5% before returning to a second low within ±1% of the first. The base is confirmed only after price subsequently rallies at least 5% from the second low. Confirmation creates a buy-limit order for the third visit to the base at 1% above the lower of the two lows. The engine avoids look-ahead: an order confirmed from a completed daily candle becomes active only from the next trading session.

The unfilled order remains active until it fills, the available history ends, or a completed daily candle closes more than 1% below the base. After entry, configurable target and stop-loss percentages control the exit; a still-open position exits at the final available close so every filled trade reports P/L and trading-session holding days. Gaps receive the opening price when it is worse or better than the resting level as appropriate. When a completed daily candle touches both stop and target and sequence is unknowable, the backtest takes the conservative stop-loss outcome.

The console will follow the existing Daily Low Trigger Backtest layout and support either every stock in one selected watchlist or one selected stock from that watchlist. It will show setup counts, fills, invalidations, target/stop outcomes, profitability statistics, P/L percentage, holding sessions, an expandable rule audit, and daily trade detail.

## Delivery result

Implemented as the **Three-Touch Base Backtest** console at `/console/base-retest-backtest`, backed by `POST /api/strategy/base-retest-backtest/run`. Focused Kotlin tests, frontend tests, the complete backend integration package, and the frontend production build pass. The daily-bar execution model honors known gaps and takes the stop first only when intraday target/stop order is genuinely unknowable.

Base discovery is deliberately independent of target and stop-loss exit timing. A later valid base is still detected when an earlier abandoned price level is never revisited, so changing the target cannot change the setup population; it changes only each setup's trade result.

## NETWEB chart validation

The supplied `NETWEB_history_6m (2).csv` actually contains 93 sessions from 2026-04-01 through 2026-08-14, so February and March cannot be reconstructed from that file alone. The April support zone evolves through multiple valid retests: the current earliest-pair rule uses 2026-04-20 and 2026-04-24, confirms on 2026-04-27, and fills on 2026-05-04. A chart-oriented interpretation can instead reuse the same zone after the 2026-05-04 retest and re-enter near 2026-05-18/20; supporting this requires an explicit recurring-base/re-arm rule rather than changing target behavior.

The July structure is valid under the current rules: 2026-07-08 low 4,153.00, 2026-07-20 low 4,129.30, confirmation on 2026-07-22, and a third-touch fill on 2026-07-24. A stricter interpretation using 2026-07-20 and 2026-07-24 as the two lows confirms on 2026-07-27, but the 2026-07-30 low is 1.47% above the lower base and therefore misses the configured +1% limit.

The 2026-06-29 losing trade exposes a confirmation-quality problem rather than a sufficient reason for a blanket 52-week-high exclusion. The 2026-06-25 candle barely crossed the 5% threshold intraday at 5,149 but closed at 4,943.80 near its low after opening at 5,130. Point-in-time price action therefore showed rejection, not a successful sign of strength. The preferred corrective rule is to require both rebounds to reach 5% on a closing basis (and preferably on a bullish confirmation candle), while treating proximity to a 52-week high plus repeated failed pushes as a distribution-risk label rather than an automatic veto. This avoids confusing legitimate high-level reaccumulation with distribution.
