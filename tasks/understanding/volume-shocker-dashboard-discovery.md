# Volume Shocker Dashboard Discovery

The current ask is a dashboard layer on top of the newly ingested `groww_volume_shocker_daily` data. The primary user goal is not generic reporting; it is faster Wyckoff review. Kush wants to look at one trading date, inspect the full top-100 volume-shocker list for that date, see the stock's LTP for the viewed day, filter the table by columns, and identify names that keep showing up across multiple dates because repeated appearance may indicate stronger institutional interest.

The dashboard also needs a quick drill-down action from a row that shows recent daily context around the selected event date, including open, close, percentage move, delivery volume, total volume, and delivery percentage. The final direction is an event-study dashboard rather than a generic screener: one selected ingested date, all 100 rows for that date, repeat/streak context, delivery build-up clues, and a clean handoff into the existing Phase 1 workflow.

## Interview Notes

This document is the live source of truth for discovery before implementation. The goal is to keep all clarified product decisions, usage nuances, and open questions in one place so implementation can stay aligned with the real review workflow rather than drift into a generic dashboard.

### Confirmed So Far

- The new data source is the daily 100-stock volume-shocker ingestion.
- The larger purpose is to strengthen Wyckoff-focused research and review.
- The user wants one dashboard where a date can be selected and the stored 100-stock snapshot for that date can be reviewed.
- The user wants the day's `ltp` visible when reviewing that date.
- The user wants table-level filtering on columns.
- The user wants an easy way to spot stocks that appear on multiple dates.
- The user wants a row action that reveals a recent event-study daily summary including open, close, percentage move, delivery volume, volume, and delivery percentage.
- The default dashboard date should be the latest available prior trading date (`T-1` behavior, or last available date if the prior day is a holiday).
- The main table should show all 100 stocks for the selected date, not a pre-filtered shortlist.
- The main table should later include a strategy/tagging column derived from the logic discussed in the external markdown, but the exact tagging behavior will be defined in more detail later.
- For repeated-appearance review, the most important signal is whether a stock appeared on back-to-back days.
- Repeat analysis should use a rolling 10-day lookback and expose both:
  - total appearances in that lookback window
  - continuous streak length in consecutive trading days
- V1 is not yet a workflow or execution product. The immediate value is observation: compare a stock's behavior before and after the volume-shocker event and learn what patterns matter.
- V2 may add action workflows such as labeling names as Wyckoff D-phase buy zone or intraday short zone, but that is explicitly later scope.
- The drill-down date range is not a fixed 5-row mini-panel. It should start 5 trading days before the selected event date and run forward through the real current date, so the user can inspect how the stock behaved after the event.
- The drill-down window should be capped at 15 trading days total: 5 trading days before the selected event date and 10 trading days after it.
- Delivery-related fields are required in the main table, not just the drill-down view.
- Delivery percentage and delivery volume are confirmed as required display fields.
- The user also wants comparison context between today's event-day delivery behavior and the prior 5-day period.
- The user refined this further: the main comparison should include:
  - `delivery volume today`
  - `delivery % today`
  - `10-day maximum delivery volume before event`
  - `delivery volume today vs 10-day maximum delivery volume before event`
- The tagging logic is intended to be part of this change, but the exact rule set still needs discussion against the shared strategy document.
- The most important user question during review is price behavior around the event, especially whether a stock qualifies more like Wyckoff Phase D, momentum/trend continuation, or intraday short behavior.
- The repeat metrics should be calculated backward from the selected event date, not from the real latest available date.
- The detail view should open inline beneath the selected row.
- A stock can have multiple tags; tags are not mutually exclusive.
- Repeat and streak calculations should use actual trading days only, within a 10-trading-day lookback window.
- The main table should default to source-rank order from the imported top-100 list.
- V1 tagging should focus on `WYCKOFF_PHASE_D` first.
- `WYCKOFF_PHASE_D` is not just a same-day volume event tag. The intended logic is to start from a stock that appears in the volume-shocker dataset, then check whether the existing `/strategy/wyckoff/phase1/run` logic suggests earlier Phase 1 style accumulation for that stock. In other words, the screen helps backtrack from the event into earlier Wyckoff buildup.
- A derived `pre_event_accumulation_hint` style signal is desired.
- The pre-event quiet-price condition for this hint should treat roughly flat behavior as between `-2%` and `+2%`.
- The inline row detail may include a compact summary above the daily table, such as appearance count, current streak, max pre-event delivery volume, and event-day vs pre-event delivery jump.
- For now, the dashboard should not embed a full bulk Phase 1 result grid inline. Instead, it should offer a button that takes the user to a dedicated Phase 1 style dashboard/workflow for the current 100 stocks. A more elegant integrated workflow can be revisited in Phase 2.
- The Phase 1 handoff should exist in both forms:
  - a top-level bulk action for the current 100 stocks
  - a row-level action for an individual stock
- Repeat information should appear directly as columns in the same 100-row table.
- The main table should also include 200-day SMA context: the 200 SMA price and how far the current/event price is from that level.
- If a stock does not qualify for the current tag, show `NO_TAG`.
- The 15-day inline detail table should visually highlight the actual event day.
- The 200-day SMA context only needs:
  - `200 SMA price`
  - `% away from 200 SMA`
- `pre_event_accumulation_hint` should be based on delivery-volume jump plus quiet price only; it does not need repeat/streak as part of the rule.
- The 15-day detail view should include:
  - `date`
  - `open`
  - `close`
  - `volume`
  - `delivery volume`
  - `delivery %`
- The v1 default main-table columns are confirmed as:
  - `rank`
  - `symbol`
  - `company name`
  - `ltp`
  - `volume`
  - `delivery volume`
  - `delivery %`
  - `10-day max delivery volume before event`
  - `today delivery vs 10-day max before event`
  - `10-day appearance count`
  - `streak length`
  - `200 SMA price`
  - `% away from 200 SMA`
  - `pre-event accumulation hint`
  - `tag`
- `delivery %` should be displayed for human review in v1 but not used in the first accumulation-hint rule.
- The first v1 tag rule is:
  - if the stock is in the volume-shocker list and the existing Phase 1 logic indicates earlier accumulation context, assign `WYCKOFF_PHASE_D`
  - otherwise assign `NO_TAG`
- The bulk Phase 1 action should directly open the existing Phase 1 page with the current 100 symbols prefilled.
- The Phase 1 handoff should prefill only; the user will click run manually.
- V1 should include a top-level quick filter for `show only pre-event accumulation hint = yes`.
- The date picker should allow only dates that actually exist in the ingested dataset.
- Multiple expanded rows may stay open at the same time.
- If some follow-up day data is missing inside the 15-day detail window, skip that day.
- V1 does not need a summary strip above the table.
- Repeated appearance should live directly inside the main table columns, not a separate table.
- Repeated appearance also needs a quick filter: `show only repeat names`.
- The row-level Phase 1 handoff should open the existing Phase 1 page with only that one stock prefilled.
- `NO_TAG` should be shown immediately on initial load.

## Implementation Outcome

The implemented v1 uses a dedicated volume-shocker dashboard page in the React console plus three new backend endpoints under `/api/strategy/volume-shocker/*`. The date selector only offers ingested dates, defaults to the latest available date before today, and loads the full stored 100-row snapshot for the selected date. Each row includes delivery volume, delivery percentage, 10-trading-day repeat count, consecutive streak length, 200 SMA price, distance from 200 SMA, a `pre_event_accumulation_hint`, and a `tag` that is either `WYCKOFF_PHASE_D` or `NO_TAG`.

Row expansion loads an inline 15-trading-day detail table lazily: 5 trading days before the event date and up to 10 trading days after it, skipping days without delivery follow-up data and highlighting the actual event day. The detail summary shows appearance count, streak, the max pre-event delivery volume, and the event-day delivery-vs-pre-event ratio. The current implementation interprets `pre_event_accumulation_hint` as a quiet-price pre-event delivery spike where the strongest delivery day in the prior 10 trading sessions closed between `-2%` and `+2%` and its delivery volume was at least `2x` the average of the other candidate pre-event delivery days.

## Delivery Observation From User

One of the most important early-buy observations is not a jump in delivery percentage alone. The stronger signal is when absolute traded volume and absolute delivery quantity suddenly expand versus prior days, while price still does not move much or even closes slightly down. Example: if a stock usually trades around 1,000 volume with 500 delivery, and then a day appears with 10,000 volume and 5,000 delivery while delivery percentage stays similar and price is flat to down about 1%, the user reads that as possible quiet accumulation. This means the dashboard should help compare absolute delivery behavior across recent pre-event sessions, not just show a single-day delivery percentage number.

### Validation Outcome

- Backend compile path through `service`: passed.
- Frontend build: passed.
- Focused new Kotlin analyzer tests: passed.
- Frontend Vitest suite: passed after adding coverage for the Phase 1 prefill contract.

## Follow-up Clarifications After First Use

Two important behavior clarifications came from live usage after the first build. First, `% away from 200 SMA` in the volume-shocker dashboard should use the same event-date close-price basis as the Phase 1 scanner, not the stored LTP field. This keeps the SMA-distance comparison consistent when the user cross-checks a stock like `SUBROS` between the two screens.

Second, when the dashboard opens the Phase 1 page with prefilled symbols, the `Universe Keys` dropdown may correctly be empty because that run mode is symbol-prefill mode, not universe-filter mode. The Phase 1 page should make that state explicit by disabling the universe selector and showing that the scan is using symbols handed off from the Volume Shocker dashboard.

Third, the refreshed Phase 1 page still needs a working universe-options source even outside the dashboard handoff flow. The page was still requesting `GET /api/screener/universes`, which the backend no longer exposed, so the stable fix is:
- add a Phase 1-owned universe-options endpoint under `/api/strategy/wyckoff/phase1/universes`
- keep a compatibility alias at `/api/screener/universes` so existing browser bundles or older links do not fail with `404 Not Found`
