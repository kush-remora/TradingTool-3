# Breakout bottleneck analysis

The current question is whether the low success rate comes primarily from stock selection, fresh-breakout detection, or the absence of a useful quality gate. The analysis will use the 23-stock `high_quality` watchlist, the existing event-driven backtest with costs/slippage, and a stock-by-stock aggregation rather than relying only on pooled results. A quality gate must be selected for a causal reason and validated out of sample; it should not be added simply because it improves the historical win rate.

The initial decision criteria are: identify whether failures are concentrated in a small number of stocks; compare the same trigger across stocks and setup characteristics; define success as target-before-stop as well as forward positive return; and report expectancy, profit factor, sample size, and stability across time. A 70% win rate is a target preference, not a mathematical requirement: with a 5% target and 5% stop before costs, 70% is roughly the break-even-plus-conviction zone, while the existing negative expectancy shows that the current combination needs improvement regardless of the exact target.

## Initial evidence — 2026-08-21

The 24-month `high_quality` replay produced 353 signals, 320 entered trades, 148 wins, 168 stops, 46.8% win rate, -0.85% expectancy, and 0.72 profit factor after the configured 0.3% transaction cost and 0.2% slippage. `FRESH_BREAKOUT` outperformed `CEILING_RECLAIM` (46.8% vs 41.4% win rate), so the fresh trigger is not yet shown to be the primary defect. The existing `BASE_QUALITY` gate produced 48.6% win rate and -0.71% expectancy, which is not a meaningful improvement.

Failures are concentrated by stock but stock rankings are unstable. The best five stocks by the full sample produced 59.7% wins and +0.60% expectancy; the worst five produced 33.8% and -2.14%. Removing the worst five only raised the remaining pool to 50.4% and -0.50%, so static stock exclusion is insufficient. Across four six-month periods, win rates were 31.1%, 56.6%, 39.7%, and 53.0%, indicating strong regime dependence. The next proposed gate is a causal trend-regime alignment gate: only allow a fresh breakout when the broad market trend and the stock's own trend are both supportive. It must first be tested walk-forward; it is not approved as a production filter yet.

## Trend-alignment test result — 2026-08-21

The proposed gate was implemented diagnostically using NIFTY 50 daily candles (instrument token 256265). Market alignment required NIFTY above a rising 200-day average. Stock alignment required the stock above a rising 200-day average and outperforming NIFTY over both 3 and 6 months. The combined aligned group performed worse than the unaligned group: 37.0% vs 49.8% win rate, -1.79% vs -0.57% expectancy, and 0.49 vs 0.80 profit factor over 24 months. The same direction appeared over 12 months (30.3% aligned) and the latest 6 months (one completed aligned trade, a loss). The gate is rejected and remains diagnostic-only.

## Next-session dip experiment — 2026-08-21

The backtest now records the first session after each breakout: its low relative to the breakout close, its low relative to the breakout-day low, its low relative to its own open, and the breakout-day close-to-close move. The 24-month `high_quality` replay covered 353 signals, 320 entered trades, 31 gap skips, and 316 completed trades. The baseline remained 46.8% target-before-stop, -0.85% expectancy, and 0.72 profit factor after costs and slippage.

Using the next session's low as a label—not as an entry rule—the current next-open trades showed a monotonic result:

| Next-session low vs breakout close | Completed | Win rate | Expectancy | Profit factor |
| --- | ---: | ---: | ---: | ---: |
| Low above breakout close | 43 | 72.1% | +1.70% | 2.07 |
| 0% to 1% below | 102 | 50.0% | -0.51% | 0.82 |
| 1% to 2.5% below | 117 | 43.6% | -1.18% | 0.64 |
| More than 2.5% below | 54 | 27.8% | -2.82% | 0.31 |

The intraday low versus next-session open told the same story: a dip below 3% had 16.7% wins and -4.07% expectancy, while a dip below 1% had 59.1% wins and +0.47% expectancy. This supports “shallow retest/price acceptance” as a useful behavior hypothesis, but it does not prove that the next-day low can be used as a filter because that low is only known after the fact.

The breakout-day move buckets did not reproduce the earlier assumed pattern in this sample: moves below 3% had 42.3% wins and -1.36% expectancy, 3%–6% had 53.4% and -0.19%, and above 6% had 48.0% and -0.56%. That measure remains diagnostic-only.

Decision: do not add a hindsight dip filter or change the production entry to a guessed intraday low. The evidence says the current next-open entry is weakest when the following session rejects the breakout deeply. The next causal experiment should test waiting for next-session acceptance and entering on the following session, or a limit order at the breakout close with explicit fill rules.

## Stock-wise review workflow — 2026-08-21

Because the pooled result has not yet isolated a reliable quality gate, the backtest UI now defaults to a 6-month run and exposes the existing per-symbol reports in a Stock-wise results tab. Each stock row shows signals, entered trades, targets, stops, gap skips, win rate, expectancy, profit factor, and end-of-test trades. Expanding a row shows that stock's complete trade history, while the All trades tab preserves the pooled view. The history window remains configurable so failures can be reviewed at scale without changing the strategy logic.

## Six-month prepared-watchlist replay — 2026-08-21

The `high_quality` watchlist was replayed across all 23 stocks from 2026-02-21 through 2026-08-21 using the fresh-breakout trigger, 5% target, 5% stop, and the same fixed execution assumptions as earlier runs. This shorter window is appropriate for this watchlist because its members were selected for recent momentum.

| Run | Signals | Entered | Gap skips | Filtered | Completed wins/stops | Win rate | Expectancy | Profit factor |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| No quality filter | 100 | 87 | 11 | 0 | 44 / 39 | 53.0% | -0.20% | 0.92 |
| Base-quality gate | 100 | 40 | 2 | 56 | 22 / 15 | 59.5% | +0.39% | 1.17 |

The prepared watchlist improves materially versus the 24-month pooled sample (46.8% win rate and -0.85% expectancy). The base-quality gate is directionally useful in this six-month momentum regime, but it removes more than half the signals and still does not establish a 70% win-rate system. It should remain an experiment, not a permanent conclusion.

The other diagnostics were too sparse or inconsistent to promote: only 1 clean volume footprint and 1 footprint with a warning appeared among 83 completed baseline trades; only 1 completed trade was trend-aligned; next-session lows above the breakout close produced 66.7% wins from 15 trades, while the other dip buckets clustered around 48%–52%; and breakout-day moves below 3% had 44.7% wins versus 55.6% for 3%–6% and 66.7% above 6%. These are observations from a short, momentum-selected sample, not gates.
