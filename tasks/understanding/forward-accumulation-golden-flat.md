# Forward accumulation: regression shape detection

The prior percentage-based flat and Golden Flat heuristic was withdrawn because it did not follow the Hybrid Validation Pipeline. Shape detection now uses the documented quadratic regression model, not an added range/200-DMA filter.

First group Accumulation hits that are at most 15 trading sessions apart. For each chain, use its final validation date as the window end. A single 2026-04-23 hit with no following trigger is therefore valid: fetch the 60 trading sessions ending on 2026-04-23. Fit normalized closing prices to `y = ax² + bx + c`; curvature `a` distinguishes cup and inverted-U distribution, while slope `b` distinguishes flat, downward, and upward linear shapes. The stored Base dates are this 60-session analysis window; original chain hit dates and regression metrics remain in JSONB details.

The saved 1-day and 1-week replay periods remain in scope and persist a named period so they stay distinct from monthly runs.

Cup and invalid inverted-U decisions now require an actual turning point inside the 60-session window: the fitted slope must be negative at the left edge and positive at the right edge for a Cup (the reverse for inverted-U). A curved line that stays directional is therefore an upward or downward drift, not a Cup. Each snapshot records curvature, center slope, edge slopes (all slopes expressed as percentage per 10 sessions), and the fitted turning-point position. The table shows a compact decision metric and exposes the complete values on hover.

When sorting the table by Phase D or Breakout, the intended review unit is a stock, not one isolated base. The default display therefore ranks stock blocks by their latest confirmation date, keeps each stock's bases adjacent, and shows its newest base first within a descending sort. The visible `+n` confirmation date is also the latest date used for sorting. A compact toggle permits raw row ordering when needed.

Chain clustering remains evidence grouping only. At each individual Accumulation hit, its preceding 60 trading sessions are split into three consecutive 20-session chunks. Each chunk is classified independently, and the latest chunk is the current visible shape; the three-part path remains visible and is saved for later validation. `FLAT_GOLDEN` is a strict separate result only when that latest 20-session chunk has near-zero direction (at most 0.35% per 10 sessions) and near-zero curve (absolute curvature at most 0.02). It does not use any candles after that hit date.
