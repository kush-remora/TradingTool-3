# Volume Footprint Experiment

## Understanding

The hypothesis is that a breakout is more reliable when, during the previous 20 sessions, a high-volume event occurred while price was still relatively cheap in the range, price finished reasonably strong, and the event low was respected afterward. The breakout should also have no later high-volume weak/rejection bar that suggests distribution or invalidates the footprint. This is a diagnostic experiment only; it is not a quality gate.

## Initial causal rules

- Baseline volume for each candidate day: median volume of the preceding 20 sessions, excluding the candidate day.
- Footprint candidate: volume at least 5× that baseline, close in the lower 60% of the prior-20-session price range, and the candle closes above its open with close in at least the upper half of its own range.
- Footprint survives when no later pre-breakout close falls below the candidate candle's low.
- Distribution warning: after the footprint, a later candle has at least 2× its own preceding-20-session median volume and closes down near the low, or invalidates the footprint low.
- PASS requires a surviving footprint, no distribution warning, and a breakout close in the upper 60% of its own range.
- WAIT means a footprint exists but the breakout close is not strong enough. REJECT means no qualifying footprint, invalidation, or distribution warning.

The thresholds are explicit hypotheses, not conclusions. Results will be compared with target-before-stop and 5/10/20-session continuation on the 23-stock `high_quality` watchlist.

## First replay result

Six months produced 100 signals but only 2 footprint candidates, so it was too sparse to evaluate. The same rule was then run over 24 months, 2024-08-21 through 2026-08-21:

- 353 signals, 320 entered trades, 31 gap skips.
- Baseline target-before-stop result: 148 wins / 168 stops, 46.8% win rate, −0.85% expectancy, 0.72 profit factor.
- 18 signals had a qualifying footprint.
- Footprint with no later warning: 5 signals, 5 entered, 4 wins = 80.0%.
- Footprint with a later warning: 13 signals, 11 entered, 3 wins = 27.3%.
- No footprint: 333 signals, 304 entered, 141 wins = 46.4%.

The clean-footprint group is directionally encouraging, and the warning group is materially weaker. However, five clean events are not enough to promote this to a gate. The current conclusion is: retain this as a diagnostic and collect more history; do not filter entries yet.
