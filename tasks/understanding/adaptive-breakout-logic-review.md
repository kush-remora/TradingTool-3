# Adaptive Breakout — Business Logic Review Findings

Date: 2026-08-19
Scope: `core/src/main/kotlin/com/tradingtool/core/strategy/adaptivebreakout/` (engine, quality gates, chart context, backtest).
Source: two review rounds on the core logic. **Performance issues are intentionally excluded** (tracked separately for a later pass).
Status: `open` = not yet discussed · `agreed` = fix direction agreed · `fixed` = implemented and verified · `rejected` = discussed, decided not to change.

---

## A. Structure Engine (`AdaptiveBreakoutEngine.kt`)

### BL-01 — Same-candle self-confirmation: a pivot can confirm itself
- **Status:** open
- **Where:** `replayCandle` runs `updateExtremes` (lines 88–93) *before* `detectTurn` (lines 94–99). A candle that makes a new leg-high and closes ≥1 ATR below its own high confirms its own high as a ceiling in the same bar (`anchorIndex == confirmedIndex`). Same for floors.
- **Why it matters:** Industry pivot/zigzag logic requires the pivot to be in the past, confirmed by *subsequent* bars. With a 1 ATR threshold, one volatile reversal day manufactures structure. Pinned as accepted behavior by the test `records a breakout even when the same candle confirms a newer ceiling`.
- **Fix direction:** Quantify first — count same-candle confirmations in a backtest run. If large, require anchor index < confirmation index (minimum 1-session separation), or treat same-candle as candidate-only.

### BL-02 — Ceiling width frozen at anchor-day ATR
- **Status:** open
- **Where:** `buildCeiling` (lines 466–491) sets `upperBoundary = high + atrAtAnchor × 0.5` once; never re-measured.
- **Why it matters:** Anchor days are usually high-ATR reversal days, so the buffer is set at peak volatility and stays wide even if the stock tightens into a low-ATR base for months. The module is named "adaptive" but the width never adapts.
- **Fix direction:** Re-measure ATR at evaluation time, or narrow the boundary as the base matures (volatility contraction).

### BL-03 — No ceiling staleness / expiry
- **Status:** fixed
- **Where:** No age check anywhere; `ceilingAgeSessions` (line 30) is computed for display but never filters anything. A ceiling confirmed 2–3 years ago can still emit `FRESH_BREAKOUT`.
- **Why it matters:** Old, distant resistance is not a meaningful breakout level (O'Neil base-count conventions; Wyckoff trading-range relevance). The backtest tests recent breakouts against full 5-year structure.
- **Fix direction:** Gate signals by ceiling age (e.g., ceiling must be confirmed within N sessions), or decay ceiling relevance with age.

### BL-04 — Failed-breakout ratchet is monotone-up forever
- **Status:** fixed
- **Where:** `withFailedAttempt` (lines 750–757) — every failed wick sets `upperBoundary = max(upperBoundary, high)`.
- **Why it matters:** After several failed attempts the effective cap becomes the highest historical wick — a level no longer visible on any chart — while `baseUpperBoundary` stays displayed. The real breakout bar silently drifts away from what the user sees.
- **Fix direction:** Cap the number of ratchets, re-anchor after N failures / M sessions, or expire the failed-attempt premium after a time window.

### BL-05 — Structure detection ignores volume entirely
- **Status:** open
- **Where:** Floors, ceilings and `FRESH_BREAKOUT` are confirmed on price + ATR only. Volume appears only in downstream quality gates and the `volumeVsTenDayAverage` display metric.
- **Why it matters:** Direct conflict with the Wyckoff doctrine in `AGENTS.md` — Law of Effort vs Result; "never treat a raw spike as accumulation without context validation". A close above the line on dry volume is a suspect breakout at the *structure* level, not just a quality note.
- **Fix direction:** Add a volume condition to `FRESH_BREAKOUT` (e.g., breakout-day volume ≥ X× 10-day average) or explicitly document why structure stays price-only.

### BL-06 — Broken-ceiling polarity dropped (resistance never becomes support)
- **Status:** open
- **Where:** `activateCeiling` (lines 493–515): when a post-breakout swing cap forms, the old broken ceiling is discarded (filtered out by `nearestHigherCeiling` because it sits below the new ceiling).
- **Why it matters:** Broken resistance becoming support is the exact level a Phase C/D retest entry (LPS doctrine) would reference. The engine deletes the level it should be watching for a retest.
- **Fix direction:** Demote broken ceilings to a support-line register instead of dropping them.

### BL-07 — Status zone ≠ test zone
- **Status:** open
- **Where:** `currentStatus` (line 609) says `TESTING_CEILING` only when `close >= anchorPrice`; `updateCeilingTest` (lines 554–579) counts touches from `lowerBoundary` (anchor − 0.5 ATR).
- **Why it matters:** A close in [lower, anchor) increments the test count while the scanner row displays "Below ceiling" — the count and the status tell different stories.
- **Fix direction:** Use one band definition for both.

### BL-08 — `testCount` starts at 1 on the confirmation day
- **Status:** open
- **Where:** `buildCeiling` line 488 (`testCount = 1`, `lastTestDate = confirmedDate`).
- **Why it matters:** A ceiling showing "test 2" has been retested only once; the birth counts as a test. Off-by-one in what the UI communicates.
- **Fix direction:** Start at 0, or rename to make the semantics explicit.

### BL-09 — `breakoutEvidence` can reference a stale breakout
- **Status:** open
- **Where:** Line 50 uses `state.latestBreakoutDate` regardless of age or whether structure has moved on (new ceiling formed above since).
- **Why it matters:** Scanner rows can show BELOW_CEILING with "breakout evidence" from a breakout weeks old — misleading context on non-breakout rows.
- **Fix direction:** Suppress evidence when the breakout belongs to a superseded ceiling generation, or cap evidence age.

### BL-10 — Engine explanations interpolate raw Doubles
- **Status:** open
- **Where:** e.g. `ceilingConfirmationExplanation` (lines 537–552) — `"Price rejected $highPrice by at least $rejectionAtrMultiple ATR using the peak-day ATR $peakAtr"`. No rounding anywhere in engine explanations (chart context rounds; engine doesn't).
- **Why it matters:** The audit drawer can render `231.41000000000003`. Also: `STRONG_REBOUND` explanation hardcodes "two ATR" instead of interpolating `strongReboundAtrMultiple` (line 630) — text lies if config changes.
- **Fix direction:** Round all interpolated values; interpolate config values instead of hardcoded words.

---

## B. Backtest Realism (`AdaptiveBreakoutBacktestEngine.kt`)

### BL-11 — No transaction costs or slippage
- **Status:** fixed
- **Where:** `simulateTrade` — entry at open, exit at exact level, zero cost model.
- **Why it matters:** For NSE swing trades, 0.2–0.4% round trip (STT, stamp duty, slippage) materially changes expectancy on 5%/5% target/stop. Win-rate numbers are currently optimistic by construction.
- **Fix direction:** Add a configurable round-trip cost parameter applied to every trade.

### BL-12 — Gap-through fills booked at exact level prices
- **Status:** fixed
- **Where:** Lines 175–184: `exitPrice = targetPrice or stopPrice` regardless of where the candle opened. If a candle *opens* beyond the stop (gap down >5%), the trade still records exactly −5%; the real fill is the open, which is worse. Same on targets (booked at target even if it opened above).
- **Why it matters:** Systematically flatters both wins and losses on gap days. No test covers an open-through-level candle.
- **Fix direction:** `exitPrice = if (open beyond level) open else level` for both stop and target.

### BL-13 — Asymmetric entry gap guard (no downside invalidation)
- **Status:** open
- **Where:** Lines 126–146 guard only gap-*up* > `maxEntryGapPct`. A gap-*down* back below the broken line — the breakout already failing — is still bought at open.
- **Why it matters:** The signal is invalidated but the trade is taken. Standard breakout practice: skip entry if the next open is back below the breakout boundary.
- **Fix direction:** Add a downside invalidation: skip if next open < breakout boundary (or breakout close − X%).

### BL-14 — Fixed % target/stop ignores the engine's own structure
- **Status:** open
- **Where:** Lines 148–149: `targetPrice/stopPrice = entryPrice × (1 ± pct)`.
- **Why it matters:** The engine builds ceilings/floors, then the backtest exits at flat ±5%. This tests an arbitrary overlay, not whether the structure detection has edge. Wyckoff doctrine (Law of Cause and Effect) wants measured-move objectives; structural practice wants stops below the breakout line / base, not at a flat %.
- **Fix direction:** Add alternative exit modes: structural stop (close back below breakout line) and/or measured-move target (base height projected). Compare against fixed-% runs.

### BL-15 — Quality gates never filter entries
- **Status:** open
- **Where:** `run()` lines 42–66: `qualityResponse` is computed per breakout and attached to the trade — never used to accept/reject the entry.
- **Why it matters:** The whole quality layer exists to answer "should I take this signal?" but the backtest can't answer "what if I only took PASS setups?" The old hard-gate failure (REJECT winning 55%) was discovered precisely because this analysis was possible — the capability should be a first-class filter, not manual eyeballing.
- **Fix direction:** Add an entry-filter option (ALL / PASS-only / PASS+WAIT) to the backtest request.

### BL-16 — Summary lacks expectancy / payoff metrics
- **Status:** fixed
- **Where:** `AdaptiveBreakoutBacktestSummary`: win rate + average holding only.
- **Why it matters:** Win rate alone is what made the old REJECT gate look useless without explaining why. Expectancy per trade, average win vs average loss (payoff), and profit factor are the minimum set to judge a signal.
- **Fix direction:** Add expectancy %, payoff ratio, profit factor to the summary (all computable from existing trade list).

### BL-17 — Survivorship bias in watchlist backtest
- **Status:** open
- **Where:** `runWatchlistBacktest` uses *today's* index constituents over *historical* candles.
- **Why it matters:** Stocks that were dropped from the index (usually after poor performance) are excluded from history — results are upwardly biased. Standard caveat for index-constituent backtests.
- **Fix direction:** At minimum, document the bias on the backtest page. Proper fix (historical constituents) is likely out of scope for a personal tool.

### BL-18 — No market-regime filter
- **Status:** open
- **Where:** Nothing in the backtest references index candles or breadth.
- **Why it matters:** Breakout systems live and die by regime; signals taken in a falling index have materially worse expectancy. Without a regime dimension, the backtest averages good and bad regimes together.
- **Fix direction:** Tag each trade with index state (e.g., NIFTY above/below 200 SMA at breakout date) so results can be split by regime.

---

## C. Quality Gates (`BreakoutDayQualityAnalyzer.kt`)

### BL-19 — Delivery gate: flat 55/40 single-day snapshot, ignores project doctrine
- **Status:** rejected as a Breakout Quality finding; fixed in Phase 1 configuration
- **Where:** Lines 208–234; `DELIVERY_PCT_PASS = 55.0`, `DELIVERY_PCT_WAIT = 40.0` for all stocks. Only the breakout day's delivery print is used.
- **Why it matters:** `AGENTS.md` mandates cap-adaptive thresholds (Small >70%, Micro >85%, Nano >92%) and the Phase 1 scanner uses rolling density (≥4 breaches in 15d) + Z-score ≥ 2.0 vs 60-day baseline. Small/micro caps currently get a free pass at the large-cap bar, and none of the rolling-density/Z-score machinery is reused.
- **Fix direction:** Cap-adaptive thresholds (needs market-cap bucket per symbol) + delivery trend over the pre-breakout window instead of one print.

### BL-20 — Buyer-days counts gap-down green candles
- **Status:** open
- **Where:** Line 99: `c.close > c.open`.
- **Why it matters:** A candle that gaps down and closes green but *below* the prior close still scores as "buyers in control". That's not buyer control.
- **Fix direction:** Use close vs prior close, or require both conditions.

### BL-21 — The "5D" rubric silently degrades to 3-session windows
- **Status:** open
- **Where:** `PRE_BREAKOUT_WINDOW = 5`, `MIN_CONTEXT_WINDOW = 3` (lines 326–329). Buyer-days PASS needs ≥4 — mathematically unreachable with a 3-session window.
- **Why it matters:** Thin-history stocks are scored under a harsher rubric while the UI label still says "5D pre-breakout". Same label, different bar.
- **Fix direction:** Require the full 5 sessions (mark UNAVAILABLE otherwise), or scale thresholds to window size.

---

## D. Chart Context (`BreakoutChartContextAnalyzer.kt`)

### BL-22 — <1 ATR room REJECT cliff at the level being broken
- **Status:** open
- **Where:** Lines 33–35: `roomAtr < 1.0 → REJECT`. The obstacle can be the prior 52-week high that the breakout is *about to clear*.
- **Why it matters:** The rule punishes the exact moment before a major-level breakout: 0.9 ATR under the prior 52w high = REJECT; clearing it = PASS. A classic 52w-high breakout setup will read REJECT right up to the break.
- **Fix direction:** REJECT only when the obstacle sits between price and the profit target — not when the obstacle *is* the level being broken. Or soften to WAIT within 1 ATR of a major level.

### BL-23 — SMA50 slope window too twitchy
- **Status:** open
- **Where:** `SMA50_TREND_SESSIONS = 5`, `SHORT_TERM_FLAT_TOLERANCE_PCT = 0.1` (lines 280–284).
- **Why it matters:** A 50-SMA barely moves 0.1% in 5 sessions for quiet bases → reads "flat" → WAIT → overall WAIT. Low discriminatory power; adds noise to the hard decision.
- **Fix direction:** Widen to 10–20 sessions, or remove from the hard decision and keep as display context.

### BL-24 — Inconsistent 52-week-high definitions
- **Status:** open
- **Where:** Engine `fiftyTwoWeekHigh` (lines 678–679) includes today (`takeLast(252)`); chart context `priorFiftyTwoWeekHigh` (line 18) excludes today (`dropLast(1)`).
- **Why it matters:** Two metrics with the same name shown in the same UI measure different things.
- **Fix direction:** Rename for clarity (e.g., `fiftyTwoWeekHighInclToday` vs `priorFiftyTwoWeekHigh`) or align definitions.

---

## E. Parameter Validation

### BL-25 — Core ATR thresholds (1.0 / 0.5) are unvalidated
- **Status:** open
- **Where:** `AdaptiveBreakoutConfig` defaults: `floorReboundAtrMultiple = 1.0`, `peakRejectionAtrMultiple = 1.0`, `compactPeakRejectionAtrMultiple = 0.5`.
- **Why it matters:** Standard zigzag/pivot practice uses looser reversal thresholds (2–3 ATR or 5–8%). At 1 ATR, choppy ranges mint ceilings constantly. Not necessarily wrong — but nothing proves the choice.
- **Fix direction:** Parameter sensitivity run (0.75 / 1.0 / 1.5 / 2.0) over the backtest; compare signal counts and expectancy.

---

## Deferred (excluded from this document — performance / ops pass later)

- O(n²) engine re-evaluation per breakout inside the backtest loop.
- Synchronous multi-minute watchlist backtest over one HTTP POST.
- No persisted daily scan snapshots (audit / forward-flow tracking).
- Single-stock backtest requires client-supplied `instrumentToken` (scan resolves it server-side).
- No test pinning the causality invariant (prefix evaluation ≡ full-replay step) the backtest relies on.
- No feature journal at `docs/features/adaptive-breakout/`.

---

## Discussion Log

_(Filled as each point is discussed and decided.)_

| # | Decision | Notes |
|---|----------|-------|

## Review Update — 2026-08-21

The last-three-day changes were reviewed as a combined commit and working-tree change set. Confirmed fixes in this pass: backtest summaries now call the selected trigger count `signalCount`; the response carries `entryTrigger`; negative entry-gap thresholds are rejected; skipped-gap trades have nullable fields in the TypeScript contract and are labelled correctly in the UI; and the buy-review fixture now matches the implemented four-check analyzer.

The proposed absolute-delivery/climax-spread quality gate in `docs/features/backtest-quality-gate.md` is not implemented. The current analyzer intentionally records four quality checks plus delivery context without filtering entries. This pass fixes BL-03, BL-04, BL-11, BL-12, and BL-16; the remaining findings stay open or explicitly deferred and do not silently change their trading semantics.

## Implementation Scope — 2026-08-21

The user approved implementing the remaining structural and backtest-realism recommendations while explicitly deferring quality-based entry filtering. The stable base ceiling remains unchanged when price makes a higher failed attempt; the implementation adds ceiling-generation expiry and bounded failed-attempt behavior rather than promoting every higher price into a new ceiling. The backtest now uses realistic gap-through exit fills, configurable costs/slippage, richer performance metrics, and a causal replay optimization with regression coverage.

Delivery ownership remains split deliberately: Phase 1 anomaly scanning now owns cap-adaptive delivery thresholds, while Breakout Day Quality continues to report delivery as context only. Relative strength, VCP-style structure, market-regime gating, and quality-filter toggles remain deferred until the current model is validated.
