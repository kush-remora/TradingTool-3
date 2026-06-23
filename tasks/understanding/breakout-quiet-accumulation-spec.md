# Breakout Quiet Accumulation Spec

This discussion started from a repeated breakout pattern seen in discretionary review: one or two sessions before a strong breakout day, total volume and delivery quantity expand sharply, but price does not move much. Then the breakout day arrives with large price expansion plus very high volume, delivery quantity, and delivery percentage. The user's working heuristic is simple and visual: compare today's volume and delivery quantity against the maximum values from the prior 10 trading days.

The product direction has now been refined. V1 should start from the full `stock_delivery_daily` table across roughly 4,000 stocks and use delivery-led filtering as the first pass. Only after that first pass should the system fetch and compute price-led context such as current price location, 200 SMA distance, RSI, and whether the stock appears to still be in a practical buying zone. This keeps the first filter cheap, simple, and tightly aligned with the user's observation that delivery behavior is the first clue.

The working v1 decision is therefore a two-stage backward sweep. Stage 1 scans all stocks for unusual delivery behavior using simple recent-history comparisons, with the user's "prior 10-day maximum" heuristic as the baseline rule. Stage 2 enriches only shortlisted names with price and indicator context so the final output answers the next practical question: is this stock still near a buyable zone, or has the move already become extended? The earlier quiet-accumulation-before-breakout pattern remains useful, but it should sit inside the broader delivery-first screening flow rather than define the whole product.

## Interview Decisions Captured

- First-pass shortlist rule:
  - `delivery quantity > prior 10-day max`
  - `volume > prior 10-day max`
- There is no delivery-% threshold rule in v1. Delivery % may be displayed later as context, but it is not part of first-pass filtering.
- Reference window is fixed to the prior `10` trading days for v1.
- The primary product objective is not just "is it in a buy zone now"; it is more importantly to surface the pre-breakout pattern, while still checking whether the stock is near the `200 SMA`.
- Buy-zone context in v1 should be simple: only proximity to the `200 SMA`, using an upper band of `+2%` above the `200 SMA` and no lower bound limit.
- Quiet pre-breakout price behavior should be checked on `D-1` and `D-2`.
- Main output should be a ranked table of current candidates only.
- The scanner should sweep all stocks present in `stock_delivery_daily` every day.
- Universe or market-cap views are downstream filters only, not part of the initial scan universe.
- Ignore low-liquidity names where current volume is below `10,000`.
- The output should show both:
  - stocks where today is still quiet but delivery-led behavior is interesting
  - stocks where breakout confirmation is already visible today
- Confirmed-breakout names should rank ahead of still-quiet names so the user can validate whether the logic is working.
- Stocks without the quiet-price clue on `D-1` or `D-2` should still remain in the output, but rank below those with the clue.
- Ranking priority:
  - primary: highest `delivery quantity / prior 10-day max delivery quantity`
  - secondary: highest `volume / prior 10-day max volume`

## Engineering Rule Sheet

### Goal

Build a daily delivery-first scanner that sweeps all stocks in `stock_delivery_daily`, identifies unusual absolute delivery participation versus the prior 10 trading days, and then enriches only those shortlisted names with price-location context and pre-breakout pattern clues.

### Data Source Assumptions

- Primary source table: `stock_delivery_daily`
- Required base fields from that table or its immediate join path:
  - `symbol`
  - `trading_date`
  - `volume`
  - `delivery_quantity`
  - `delivery_percentage` if available, display-only for now
- Required enrichment fields from price/history source:
  - `close`
  - `200_sma`

### Scan Date

- Run the scanner for one anchor date `D`.
- Default `D` should be the latest available trading date in `stock_delivery_daily`.
- All comparisons use actual trading sessions, not calendar days.

### Stage 1: Delivery-First Shortlist

For each stock on anchor date `D`:

1. Exclude the stock if current-day `volume < 10,000`.
2. Build a 10-trading-day historical window using `D-10` through `D-1`.
3. Compute:
   - `prior_10d_max_volume`
   - `prior_10d_max_delivery_quantity`
4. Keep the stock only if:
   - `volume > prior_10d_max_volume`
   - `delivery_quantity > prior_10d_max_delivery_quantity`
5. Compute Stage 1 strength fields:
   - `volume_ratio_vs_10d_max = volume / prior_10d_max_volume`
   - `delivery_ratio_vs_10d_max = delivery_quantity / prior_10d_max_delivery_quantity`

### Stage 2: Pattern Classification

For every Stage 1 stock, classify whether today looks like a confirmed breakout or an early quiet setup.

#### A. Quiet Pre-Breakout Clue

Inspect `D-1` and `D-2` separately.

For each candidate day `X` in `{D-1, D-2}`:

1. Build that day's prior 10-trading-day window.
2. Compute:
   - `x_prior_10d_max_volume`
   - `x_prior_10d_max_delivery_quantity`
   - `x_pct_change = ((close_x - previous_close_x) / previous_close_x) * 100`
3. Mark `X` as a quiet-accumulation clue if:
   - `volume_x > x_prior_10d_max_volume`
   - `delivery_quantity_x > x_prior_10d_max_delivery_quantity`
   - `x_pct_change <= 2`

Derived fields:
- `has_quiet_clue` = true if either `D-1` or `D-2` qualifies
- `quiet_clue_day` = `D-1`, `D-2`, or null

Note:
- There is no lower bound on `x_pct_change` in v1.
- This intentionally allows flat, slightly red, or even more negative days if participation was strong.

#### B. Confirmed Breakout Today

Today should be flagged as a confirmed breakout only for validation and ranking visibility.

V1 breakout confirmation rule:
- `close_pct_change_on_D > 2`

Derived field:
- `is_confirmed_breakout_today`

Reasoning:
- The exact breakout threshold can be tuned later, but `> 2%` is the simplest first split between "still quiet" and "visibly moving."

### Stage 3: Buy-Zone Context

For every Stage 1 stock, compute:

- `distance_from_200_sma_pct = ((close - sma_200) / sma_200) * 100`

Derived field:
- `is_near_200_sma = distance_from_200_sma_pct <= 2`

Notes:
- There is no lower bound in v1.
- This is context, not a hard filter.
- A stock far below the `200 SMA` may still appear in output if the delivery pattern is strong.

### Ranking Rules

Apply ranking in this order:

1. `is_confirmed_breakout_today = true` before `false`
2. `has_quiet_clue = true` before `false`
3. Higher `delivery_ratio_vs_10d_max`
4. Higher `volume_ratio_vs_10d_max`

This preserves the user goal:
- breakout names first for validation
- quiet clue names above generic shock names
- strongest delivery expansion first

### Output Table: V1 Required Fields

Minimum day-one fields:

- `symbol`
- `date`
- `close`
- `volume`
- `delivery_quantity`
- `delivery_percentage` if available
- `prior_10d_max_volume`
- `prior_10d_max_delivery_quantity`
- `volume_ratio_vs_10d_max`
- `delivery_ratio_vs_10d_max`
- `has_quiet_clue`
- `quiet_clue_day`
- `is_confirmed_breakout_today`
- `200_sma`
- `distance_from_200_sma_pct`
- `is_near_200_sma`

### Optional Display Labels

Simple labels are enough for v1:

- `CONFIRMED_BREAKOUT_WITH_CLUE`
- `CONFIRMED_BREAKOUT_NO_CLUE`
- `QUIET_SETUP_WITH_CLUE`
- `DELIVERY_SHOCK_NO_CLUE`

These are display helpers only. They should be derived from the ranking fields, not from a separate rule engine.

### Edge Cases

- If prior 10-day history is missing, exclude the stock from the scan.
- If `prior_10d_max_volume` or `prior_10d_max_delivery_quantity` is zero, exclude the stock to avoid meaningless ratios.
- If `200 SMA` is unavailable, keep the stock but leave buy-zone fields null.
- If `delivery_percentage` is unavailable, do not block the record.

### Recommended Backend Shape

Keep the implementation readable:

1. Query anchor-day stock rows from `stock_delivery_daily`
2. Exclude low-liquidity rows
3. Load prior 10-day history only for the remaining symbols
4. Apply Stage 1 filters
5. Enrich surviving symbols with price and `200 SMA`
6. Compute quiet-clue and breakout-context flags
7. Return one ranked table payload

### Validation Checklist

- Confirm the scan excludes all rows where current-day volume is below `10,000`.
- Confirm Stage 1 uses only the prior 10 trading sessions.
- Confirm a stock with strong current delivery and volume but no quiet clue still appears lower in the table.
- Confirm a stock with confirmed breakout today ranks above a still-quiet stock with similar ratios.
- Confirm `delivery %` never blocks inclusion in v1.
- Confirm `200 SMA` proximity is informational, not a required filter.

## Implementation Plan: Delivery-First Breakout Validation Scanner

### Overview

Implement this as a new sibling feature to the existing volume-shocker dashboard, not as an extension of Wyckoff Phase 1. The backend should own the scan and ranking logic, expose one read-only API for the table, and the frontend should render one ranked review table with minimal controls.

### Requirements

- Sweep all stocks in `stock_delivery_daily` for one anchor date `D`.
- Exclude low-liquidity rows where current-day volume is below `10,000`.
- Shortlist only names where both current-day volume and current-day delivery quantity exceed their prior 10-trading-day maxima.
- Classify shortlisted names into:
  - confirmed breakout today
  - still quiet today
  - quiet clue on `D-1` or `D-2`
- Enrich shortlisted names with `close`, `200 SMA`, and `% from 200 SMA`.
- Return one ranked table payload.
- Keep universe and market-cap filtering out of the backend scan logic for v1.

### Architecture Changes

- New backend strategy package:
  - `core/src/main/kotlin/com/tradingtool/core/strategy/deliverybreakout/`
- New JDBI read methods:
  - `core/src/main/kotlin/com/tradingtool/core/delivery/dao/StockDeliveryReadDao.kt`
- New API endpoint:
  - `resources/src/main/kotlin/com/tradingtool/resources/StrategyResource.kt`
- New frontend hook/page additions:
  - `frontend/src/hooks/useDeliveryBreakoutScanner.ts`
  - `frontend/src/pages/DeliveryBreakoutScannerPage.tsx`
  - `frontend/src/types.ts`
  - `frontend/src/App.tsx`
- New journey note after implementation:
  - `wyckoff-market-cycle/docs/journeys/2026-06-23.md` or a new same-feature day note if implemented later

### Existing Patterns To Reuse

- Backend resource style from:
  - `resources/src/main/kotlin/com/tradingtool/resources/StrategyResource.kt`
- Service + models pattern from:
  - `core/src/main/kotlin/com/tradingtool/core/volumeshocker/groww/GrowwVolumeShockerDashboardService.kt`
  - `core/src/main/kotlin/com/tradingtool/core/volumeshocker/groww/GrowwVolumeShockerDashboardModels.kt`
- Frontend fetch hook pattern from:
  - `frontend/src/hooks/useVolumeShockerDashboard.ts`
- Frontend table page pattern from:
  - `frontend/src/pages/VolumeShockerDashboardPage.tsx`

### Implementation Steps

### Phase 1: Backend Scan Models And Query Shape

1. **Create delivery-breakout models**  
   File: `core/src/main/kotlin/com/tradingtool/core/strategy/deliverybreakout/DeliveryBreakoutModels.kt`
   - Action: Define response models for:
     - table response
     - row shape
     - optional meta block with anchor date and scanned counts
   - Why: Keeps the API contract explicit before service logic grows
   - Dependencies: None
   - Risk: Low

2. **Add stock-delivery DAO methods for scan support**  
   File: `core/src/main/kotlin/com/tradingtool/core/delivery/dao/StockDeliveryReadDao.kt`
   - Action: Add narrowly scoped read methods for:
     - latest available trading date
     - all rows for anchor date
     - prior history for a set of instrument tokens over a bounded date window
   - Why: Existing methods are close, but the scanner should avoid repeated per-symbol DAO calls
   - Dependencies: Step 1 not required
   - Risk: Low

3. **Decide whether existing DAO methods are sufficient before adding SQL**  
   File: `core/src/main/kotlin/com/tradingtool/core/delivery/dao/StockDeliveryReadDao.kt`
   - Action: Prefer reusing:
     - `getLatestTradingDate()`
     - `findAllByTradingDate(...)`
     - `findByInstrumentTokensBetweenDates(...)`
   - Why: Minimal diffs fit repo guidance better than introducing premature custom SQL
   - Dependencies: Step 2
   - Risk: Low

### Phase 2: Backend Service Logic

4. **Create scanner service**  
   File: `core/src/main/kotlin/com/tradingtool/core/strategy/deliverybreakout/DeliveryBreakoutScannerService.kt`
   - Action: Implement the full scan flow:
     - resolve anchor date
     - load current-day delivery rows
     - drop rows with `volume < 10,000`
     - load prior delivery history for remaining tokens
     - compute prior 10-session maxima
     - apply first-pass shortlist
   - Why: This is the feature’s core logic and should remain in one readable service
   - Dependencies: Phase 1
   - Risk: Medium

5. **Add price enrichment and classification logic**  
   File: `core/src/main/kotlin/com/tradingtool/core/strategy/deliverybreakout/DeliveryBreakoutScannerService.kt`
   - Action: For shortlisted rows:
     - load candle history through `CandleCacheService`
     - compute current close
     - compute `200 SMA`
     - compute `% from 200 SMA`
     - compute current-day `% change`
     - inspect `D-1` and `D-2` for quiet-clue status
     - classify `is_confirmed_breakout_today`
   - Why: Keeps delivery-first filtering cheap while reserving heavier price work for only surviving names
   - Dependencies: Step 4
   - Risk: Medium

6. **Keep ranking logic explicit and local**  
   File: `core/src/main/kotlin/com/tradingtool/core/strategy/deliverybreakout/DeliveryBreakoutScannerService.kt`
   - Action: Apply final ordering in one place:
     - confirmed breakout first
     - quiet clue first
     - delivery ratio descending
     - volume ratio descending
   - Why: Ranking is product behavior, so it should not be hidden inside helper abstractions
   - Dependencies: Step 5
   - Risk: Low

7. **Extract tiny pure helpers only if they reduce noise**  
   File: `core/src/main/kotlin/com/tradingtool/core/strategy/deliverybreakout/DeliveryBreakoutAnalyzer.kt` if needed
   - Action: Only extract small calculator helpers for:
     - ratio calculation
     - `% change`
     - `200 SMA` distance
     - quiet-clue checks
   - Why: Avoid a bloated service without over-abstracting a first version
   - Dependencies: Step 5
   - Risk: Low

### Phase 3: API Surface

8. **Add scanner endpoint to strategy resource**  
   File: `resources/src/main/kotlin/com/tradingtool/resources/StrategyResource.kt`
   - Action: Add a GET endpoint such as:
     - `/api/strategy/delivery-breakout/dashboard`
   - Why: Matches the existing console-facing resource style
   - Dependencies: Phase 2
   - Risk: Low

9. **Support optional anchor-date query parameter**  
   File: `resources/src/main/kotlin/com/tradingtool/resources/StrategyResource.kt`
   - Action: Accept `tradeDate` query param and default to latest available date when omitted
   - Why: Keeps review reproducible while preserving easy default behavior
   - Dependencies: Step 8
   - Risk: Low

### Phase 4: Frontend Table Experience

10. **Add frontend API types**  
    File: `frontend/src/types.ts`
    - Action: Define:
      - `DeliveryBreakoutDashboardResponse`
      - `DeliveryBreakoutDashboardRow`
    - Why: Keeps the frontend strongly typed and consistent with existing pages
    - Dependencies: Phase 3 contract
    - Risk: Low

11. **Add scanner data hook**  
    File: `frontend/src/hooks/useDeliveryBreakoutScanner.ts`
    - Action: Mirror the existing volume-shocker hook:
      - load table data
      - expose loading and error state
    - Why: Reuse a known, simple fetch pattern
    - Dependencies: Step 10
    - Risk: Low

12. **Build the scanner page**  
    File: `frontend/src/pages/DeliveryBreakoutScannerPage.tsx`
    - Action: Render a ranked table with:
      - symbol
      - date
      - close
      - volume
      - delivery quantity
      - prior 10-day maxima
      - ratios
      - quiet clue flag
      - breakout flag
      - `200 SMA`
      - `% from 200 SMA`
    - Why: The user explicitly wants a ranked table of current candidates only
    - Dependencies: Steps 10-11
    - Risk: Medium

13. **Add the page to console navigation**  
    File: `frontend/src/App.tsx`
    - Action: Register a new route and menu entry, likely near `Volume Shocker`
    - Why: Makes the feature discoverable without touching unrelated screens
    - Dependencies: Step 12
    - Risk: Low

### Phase 5: Testing And Validation

14. **Add focused backend tests for scan rules**  
    Files:
    - `core/src/test/kotlin/com/tradingtool/core/strategy/deliverybreakout/DeliveryBreakoutScannerServiceTest.kt`
    - or `...AnalyzerTest.kt` if helpers are extracted
    - Action: Cover:
      - liquidity exclusion
      - prior 10-day max filtering
      - quiet clue on `D-1`
      - quiet clue on `D-2`
      - breakout-today classification
      - ranking order
    - Why: This feature is rule-driven and should be validated against exact examples
    - Dependencies: Phases 1-3
    - Risk: Medium

15. **Add frontend render test for table contract**  
    File: `frontend/src/pages/DeliveryBreakoutScannerPage.test.tsx`
    - Action: Verify:
      - data fetch success
      - table renders required columns
      - breakout rows can appear ahead of quiet rows
    - Why: Prevents regressions in the review UI
    - Dependencies: Phase 4
    - Risk: Low

16. **Run targeted validation commands**  
    Files: none
    - Action:
      - backend test command for new Kotlin tests
      - relevant backend compile path
      - frontend test for new page
      - frontend build
    - Why: The feature is not done until both rule logic and UI contract are proven
    - Dependencies: All prior phases
    - Risk: Low

### Implementation Order

Recommended order:

1. Backend models
2. Reuse-or-extend DAO reads
3. Core scanner service with Stage 1 filtering
4. Candle enrichment and classification
5. Resource endpoint
6. Frontend types + hook
7. Frontend table page
8. Tests and validation

This order keeps the API usable early and minimizes rework.

### Testing Strategy

- Unit tests:
  - ratio calculations
  - quiet-clue logic
  - breakout-today classification
  - `200 SMA` proximity calculation
- Integration-style backend tests:
  - service output ordering
  - handling of missing history
  - null `200 SMA` behavior
- Frontend tests:
  - successful fetch and render
  - empty state
  - error state
- Manual validation:
  - compare 5-10 known names against the raw daily table and chart intuition
  - confirm a few known breakout examples land above weaker delivery shocks

### Risks & Mitigations

- **Risk**: Candle enrichment for many shortlisted names may be slower than expected  
  - Mitigation: Keep Stage 1 filtering strict and only enrich survivors; reuse the `CandleCacheService` pattern already used by Phase 1 and volume-shocker.

- **Risk**: “No lower bound” on quiet clue may admit damaged breakdowns, not accumulation  
  - Mitigation: Keep this behavior in v1 because it is a deliberate user choice, but expose the quiet clue as a visible field so review remains skeptical.

- **Risk**: `200 SMA` can be unavailable for newer listings  
  - Mitigation: Keep those rows visible and show null buy-zone context rather than dropping them.

- **Risk**: The first version may overfit to one observed pattern  
  - Mitigation: The table intentionally shows both quiet and confirmed-breakout names so the rule can be validated quickly against live daily results.

### Success Criteria

- [ ] One endpoint returns a ranked daily table for the latest available delivery date.
- [ ] The scan excludes all rows with current-day volume below `10,000`.
- [ ] The scan only shortlists names where both current-day volume and delivery quantity exceed their prior 10-day maxima.
- [ ] Quiet clues from `D-1` and `D-2` are visible in the output.
- [ ] Confirmed-breakout rows rank above still-quiet rows.
- [ ] `200 SMA` proximity is shown but does not filter names out.
- [ ] Focused backend and frontend tests pass.
- [ ] A feature journey note captures implementation decisions and validation results.

## Implementation Outcome

The feature was implemented as a new delivery-breakout scanner flow under `core/.../strategy/deliverybreakout`, exposed through `GET /api/strategy/delivery-breakout/dashboard`, and rendered in a new React console page named `Delivery Breakout`. The backend now scans the latest available `stock_delivery_daily` date by default, excludes rows with current volume below `10,000`, filters on both absolute volume and absolute delivery quantity versus the prior 10 trading sessions, enriches only the shortlisted symbols with daily candle context, and returns a ranked table with quiet-clue and breakout-today flags.

The quiet-clue rule checks only `D-1` and `D-2`, prefers `D-1` when both qualify, and uses the agreed `<= +2%` price-move cap with no lower bound. Buy-zone context remains informational only and is currently based on `200 SMA` proximity with the agreed `<= +2% above SMA200` rule and no lower-bound restriction. The frontend currently focuses on the ranked table and summary counts; it does not yet add date picking or downstream filters because the user goal for v1 is daily validation of the delivery-led pattern itself.

## Validation Outcome

- `mvn -f pom.xml -pl core -am test -Dtest=DeliveryBreakoutAnalyzerTest,HotSmaScannerServiceTest -Dsurefire.failIfNoSpecifiedTests=false` passed.
- `mvn -f pom.xml -pl service -am -DskipTests compile` passed.
- `npm --prefix frontend run test:run -- DeliveryBreakoutScannerPage.test.tsx` passed.
- `npm --prefix frontend run build` passed, with the existing Vite large-chunk warning.
