# Forward accumulation: regression shape detection

The prior percentage-based flat and Golden Flat heuristic was withdrawn because it did not follow the Hybrid Validation Pipeline. Shape detection now uses the documented quadratic regression model, not an added range/200-DMA filter.

First group Accumulation hits that are at most 15 trading sessions apart. For each chain, use its final validation date as the window end. A single 2026-04-23 hit with no following trigger is therefore valid: fetch the 60 trading sessions ending on 2026-04-23. Fit normalized closing prices to `y = ax² + bx + c`; curvature `a` distinguishes cup and inverted-U distribution, while slope `b` distinguishes flat, downward, and upward linear shapes. The stored Base dates are this 60-session analysis window; original chain hit dates and regression metrics remain in JSONB details.

The saved 1-day and 1-week replay periods remain in scope and persist a named period so they stay distinct from monthly runs.

Cup and invalid inverted-U decisions now require an actual turning point inside the 60-session window: the fitted slope must be negative at the left edge and positive at the right edge for a Cup (the reverse for inverted-U). A curved line that stays directional is therefore an upward or downward drift, not a Cup. Each snapshot records curvature, center slope, edge slopes (all slopes expressed as percentage per 10 sessions), and the fitted turning-point position. The table shows a compact decision metric and exposes the complete values on hover.

When sorting the table by Phase D or Breakout, the intended review unit is a stock, not one isolated base. The default display therefore ranks stock blocks by their latest confirmation date, keeps each stock's bases adjacent, and shows its newest base first within a descending sort. The visible `+n` confirmation date is also the latest date used for sorting. A compact toggle permits raw row ordering when needed.

Chain clustering remains evidence grouping only. At each individual Accumulation hit, its preceding 60 trading sessions are split into three consecutive 20-session chunks. Each chunk is classified independently, and the latest chunk is the current visible shape; the three-part path remains visible and is saved for later validation. It does not use any candles after that hit date.

## Strict line flatness — 2026-07-19

Quadratic regression is no longer allowed to decide either `FLAT` or `FLAT_GOLDEN`. Each 20-session chunk first fits a least-squares straight line to closing prices. If one close is at least 4% away from the initial line, only that largest deviation is excluded and the line is fitted again; a second material deviation remains in the final deviations and prevents a flat result. The result is `FLAT` only when the fitted direction, typical distance, and maximum remaining distance all meet the configured limits. `FLAT_GOLDEN` is the stricter subset and may still carry one visible ignored-shock warning.

Initial Golden limits are calibrated from BHEL's 20 sessions ending 2026-03-30: after excluding 2026-03-04, its line drifts -1.57% per 10 sessions, has 1.71% typical deviation, and 3.63% maximum remaining deviation. The editable JSON thresholds start slightly above those measurements: 1.75%, 1.8%, and 3.8%. The saved snapshot JSONB retains the line fit, ignored date, and all three chunk results. Algorithm version `v8-strict-line-flatness` intentionally makes old replays stale.

The first v8 replay exposed a read-boundary compatibility issue: older JSONB details use Jackson's `[year, month, day]` date arrays, but the chunk mapper expected ISO date strings and silently dropped the complete 20–20–20 path. The mapper now supports both representations, while new writes use ISO strings. Existing v8 saved runs therefore display their chunk path without another replay.
