# 52-Week High Chartink Backtest

## Current Understanding

Kush provided a Chartink-export CSV with one row per signal candidate and four fields: `date`, `symbol`, `marketcapname`, and `sector`. The signal itself is already precomputed outside the repo from the Chartink rule: current day makes a 260-day high, market cap is at least 100, operating profit margin is at least 20%, and the prior 20-day max close stayed below the prior 240-day max close. The repo does not need to recreate the screener logic for this task; it needs to backtest the exported signals.

The first-pass backtest should stay simple and AI-friendly: enter at the next available trading day's open, simulate configurable exits, and emit a trade-level dataset large enough for downstream pattern mining. Default outputs should include overall and market-cap-bucket success rates, holding days, sector, and trade outcome fields. The initial exit policy will be a fixed profit target plus a fixed stop loss, with room to compare target-only vs target-and-stop variants if the first results look too brittle.

## Implementation Plan

- Build a typed research script under `scripts/` that reads the CSV and loads daily candles from the repo's Supabase-backed candle store.
- Simulate a long entry at the next trading day's open after each signal date.
- Support configurable exits, starting with:
  - profit target: `5%` and `10%`
  - stop loss: `5%`
  - fallback exit: final available close if neither target nor stop is hit
- Export:
  - trade-level CSV for AI analysis
  - summary CSV and JSON with overall, large-cap, mid-cap, and small-cap win rates plus holding-period stats
- Validate by running the script on `/Users/kushbhardwaj/Downloads/Backtest 52 week high first time.csv` and capturing coverage gaps or missing-price cases.

## Implementation Outcome

The implementation was moved from the original Python idea into a Kotlin-first flow that fits this repo better: a new CSV source, a pure backtest engine, a thin service that reads daily candles by symbol from Supabase, and a cron-style job that writes reports under `build/reports/chartink-fiftytwo-week-high-backtest/`. The job reads `manual-input/Backtest 52 week high first time.csv`, enters on the next trading day's open, and evaluates two default strategies: `target_5_stop_5` and `target_10_stop_5`.

Validation ran successfully against the real file. The generated outputs are `latest.json`, `latest-summary.csv`, and `latest-trades.csv`. On the current database snapshot, 244 signals produced 488 strategy rows. For `target_5_stop_5`, 181 trades had an entry candle and 92 hit target first for a `50.83%` success rate. For `target_10_stop_5`, the success rate dropped to `30.39%`. Market-cap splits were strongest in the available small-cap subset, but that bucket also had the largest data-coverage gap. The no-entry rows break down into `54` symbols with no price history in the candle store and `9` signals without a next trading day yet, which should be treated as data coverage rather than strategy failure.
