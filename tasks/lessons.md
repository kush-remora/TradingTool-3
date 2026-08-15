# Lessons

## 2026-04-12

- Do not lock onto a named external data source unless the user explicitly confirmed it. When the user says a category like "delivery source," keep the plan source-agnostic until the exact source is verified.
- For this project, treat Remora as the unfinished base of the institutional-footprint strategy, not as a separate unrelated strategy.
- For this repo, if something is configurable in day-to-day strategy operation, put it in a JSON config instead of hardcoding it in service logic.

## 2026-04-17

- For freshness-critical trading snapshots, do not use broad stale grace windows in the sync trigger path; they silently hide missing latest sessions.
- When calling historical day APIs, avoid using today's `00:00` as the end boundary; use next-day start (or current time) so today's published day candle can be included.
- Kite daily-history requests have an approximately 1,900-calendar-day limit; split longer warm-up ranges into smaller inclusive chunks before calling the API.

## 2026-04-18

- When the user explicitly asks for review/analysis first, do not start implementation. Produce a decision document with impact/risk/options, get sign-off, then code in a separate step.

## 2026-04-19

- In backtests, never use `entryPrice` as the default fallback exit price when current-rank data is missing; first attempt exit-day candle close, otherwise clearly mark pricing as unavailable.
- When result windows are user-selected but computed from sparse historical snapshots, always show both requested range and actual data-coverage range in UI to avoid false inconsistency.
- When adding frontend universe filters, ensure all action endpoints (including refresh/sync) carry the same universe parameter; otherwise the UI selection and backend refresh scope drift.

## 2026-04-21

- For earnings-event tracking in this single-user tool, prefer a single-table JSONB payload (`earnings_results.behavior_payload`) over a separate snapshot-history table unless the user explicitly asks for normalized history tables.
- For cron-backed external data sync (Groww earnings/watchlist), never swallow upstream HTTP/parse failures as empty lists; fail fast so scheduler health checks catch freshness/auth regressions.
- When the user prefers manual API copy/paste for reliability, switch cron ingest to file-based adapters instead of spending cycles on brittle authenticated scraping.

## 2026-04-23

- In manual-symbol trading flows, do not rely on local `stocks` table membership for eligibility; always resolve instrument tokens from Kite exchange instruments first so valid NSE symbols like `ARE&M` are never dropped before strategy evaluation.

## 2026-05-17

- In backtest outputs, do not report only closed trades without handling end-of-window open positions; always mark open positions to market on the final available candle (`BACKTEST_END`) so valid late entries are visible to users.
- When the user asks for discussion/alignment first (especially for UX/strategy workflow changes), do not implement immediately. First present the proposal clearly, get sign-off, then proceed with code/doc updates.
- For screener UX in this project, default to raw-data-first tables (sortable/filterable dates/types) instead of status-heavy derived logic; avoid over-engineered label systems unless explicitly requested.

## 2026-05-26

- In symbol selection UX, prefer broker/watchlist-backed selectors over freeform text by default, but keep a one-off manual symbol input for debugging so both operational and debug workflows stay intact.
- In scanner controls, do not hard-block run behind universe selection if symbol selection is already present; allow symbol-only runs for fast debug workflows.

## 2026-06-02

- When working with Ant Design tables, prefer the framework's built-in column filter UX over building a parallel external filter strip unless the user explicitly asks for a custom filtering surface.
- When the user shows a concrete UI screenshot, match that interaction pattern closely instead of extending it with a more powerful but different custom behavior.
- In cron backfill jobs, never report only aggregate failure counts when the unit of failure is a date; always include the failing dates and reasons in the summary/error so stale-data debugging is immediate.
- In operational error messages, lead with stock symbols and human-readable company names; keep broker/internal lookup keys only as secondary debug context.
- When a backfill failure threshold is meant to express data quality on a single trading date, encode it directly as a per-date unresolved-symbol rule instead of an indirect failed-date counter.

## 2026-06-19

- When the downstream use of a copied external scanner feed is still unknown, retain the complete bounded source result with its date and rank. Apply shortlist rules only after evidence establishes a useful cutoff; preserve the vendor identifier when it is the only reliable row identity.
- Unknown downstream use does not justify mirroring every vendor field. Persist only identity plus the raw inputs needed for likely calculations; omit presentation metadata and derive deterministic metrics rather than storing them.
- Keep feature-specific domain validation in the service layer when that is the chosen project boundary; SQL should retain only structural concerns such as types, required columns, defaults, and a surrogate primary key—not duplicate business rules with `CHECK` or `UNIQUE` constraints.
- Groww may omit NSE segment suffixes that Kite includes in `tradingsymbol`; keep explicit resolver fallbacks for known suffixes such as `-BE`, `-IV`, and `-SM`, with a regression test for each supported case.

## 2026-06-20

- When replacing a strategy's internal logic, do not assume the product name should change too; confirm whether the existing user-facing strategy label should be retained.
- For daily mover routing workflows, default to separate actionable views per bucket instead of one mixed result surface when the buckets represent different trading behaviors.
- For new projects or meaningful new discussions, create and maintain a compact discussion-specific understanding document under `tasks/understanding/` instead of using `tasks/todo.md`.

## 2026-07-19

- When extending JSONB-backed snapshots, treat absent, blank, or malformed legacy optional values as empty data at the read boundary; never let one old row fail an entire dashboard response.
- Before changing trading classification logic, trace it to the governing HLD and source strategy document. Do not substitute a nearby experiment's screening conditions for the documented backend shape algorithm.

## 2026-07-24

- When a user separates base discovery from trade execution, implement and label them as independent workflows. Do not carry entry, target, stop, or manual-zone assumptions into the base-definition calculation.
- For a three-week base, use exactly one low from each completed week; never substitute a rolling daily-low range or include the evaluation week in its own support calculation.

## 2026-08-08

- For rolling daily backtests, fetch at least one candle before the visible test window so the first setup candle can still receive a valid close-to-close return.
- Keep engine-facing fixture/member types at the same visibility as the engine method; Kotlin rejects public methods that expose internal parameter types.

## 2026-08-10

- When a trading rule says "the second green candle," confirm whether consecutive candles are intended before implementing the trigger; once specified, keep RSI evaluation separate from the post-signal candle confirmation.
- Treat RSI oversold as a setup, not an entry: define "RSI improved" as a later close above the fixed oversold threshold so the backtest has one clear, reproducible recovery trigger.

## 2026-08-11

- For the single-stock daily review console, optimize for one-viewport information density and simultaneous context, not progressive disclosure or navigation. Keep chart, recent tape, multi-week structure, evolving story, and note entry visible together; highlight exceptions instead of hiding raw context behind tabs.
- For trading charts with a hover readout, explicitly use a free crosshair mode. The selected date may snap horizontally to a candle, but the price guide must remain vertically movable for normal chart inspection.

## 2026-08-14

- In daily-OHLC retest backtests, treat a same-day target after an intraday limit fill as ambiguous when the session opens above the limit; require the next session unless the open itself proves the order was already filled.
- Round calculated trading prices with explicit half-up decimal rounding and compare limit touches with a small tolerance; binary floating-point boundaries can otherwise turn an exact retest into a false no-fill.
- When a hypothesis is defined as "every day," do not group anchors into calendar weeks; model each trading session independently and attach its own trigger, order window, fill, and exit lifecycle.
- For rolling-window hypotheses, calculate the previous five completed sessions before the current entry day; do not move the order to the next day after the window qualifies.
- Daily OHLC does not reveal intraday order, so an explicit candle-path assumption must be recorded: green means low-then-high, red means high-then-low, and doji is unknown.
- For dense trading audit tables, keep the decision fields in the main row and move verification fields into an expandable debug area; display volume as a ratio of the 10D average (for example, 82.58% of average), not as a confusing signed delta.
- For trade-audit drill-downs, use trading-session indices around the backtest row's cycle start and exit dates, add the requested buffer on both sides, and highlight entry/exit rows so raw daily evidence can be checked without losing the trade narrative.
- In dashboards with persistent header tools such as the buy/sell calculator, detail modals should use a non-blocking mask when the underlying tool must remain interactive; add a regression assertion that no modal mask is present.
- In trade detail tables, distinguish order, actual fill/entry, and exit dates with explicit markers; same-session entry and exit needs a combined style so one state does not visually hide the other.
- Keep a strategy's confirmation threshold separate from its exit target when only the target is made configurable; otherwise a UI target change silently changes the setup population as well.
- Discover backtest setups independently from trade exit timing. Restarting pattern discovery at the previous trade's exit can make a larger target produce more signals by skipping or getting trapped behind different price history.

## 2026-08-15

- Never derive a durable event snapshot from a bounded audit-history window. Persist the event date and its evidence explicitly; if evidence is unavailable, show that honestly instead of silently substituting the latest candle.
- In adaptive structure detection, separate local breakout resistance from older major overhead. A distant historical ceiling should remain visible as risk context, but must not suppress a confirmed breakout from a newer rejection-and-retest range.
