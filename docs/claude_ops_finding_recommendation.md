# Adaptive Breakout Engine — Audit Findings & Recommendations

**Date:** 18 August 2026  
**Scope:** Full review of `core/strategy/adaptivebreakout/` from a buying perspective, compared against industry-standard breakout practices.  
**Data basis:** Core watchlist audit through 18 Aug 2026 (8 stocks, 43 Fresh Breakouts over 6 months).

**Implementation status:** This document records audit findings and recommendations. “Approved for implementation” below means prioritized follow-up, not shipped behavior; compare with the feature journal and current tests before treating a recommendation as live.

---

## Part 1: What's Already Excellent (vs Industry)

### 1. Two-Line Failed-Attempt Model ✅
The `baseUpperBoundary` + `failedAttemptHigh` = `upperBoundary` design is ahead of retail-grade systems. Most retail scanners (Chartink, Screener.in, TradingView scripts) use a single resistance line. Professional order-flow platforms (Bookmap, Sierra Chart) track failed auction highs separately but rely on tick data. This system replicates that concept with daily OHLC.

> Keltner Channel / Donchian Channel breakout systems use a single upper band. They have no concept of "the wick that failed" versus "the base resistance." The two-line model is structurally superior for swing trading.

### 2. POST_BREAKOUT_SWING Ceiling Type ✅
After a breakout, if the stock pulls back and forms a new peak, `upperBoundary = anchorPrice` (no ATR buffer). This matches professional practice—the raw swing high is the resistance after a breakout because the previous ATR buffer was for the pre-breakout range context. Industry swing traders call this "the first higher-high test."

### 3. Circuit-Lock Awareness ✅
`rangeLocked` in evidence + `hasTradableRange()` in backtester + updated close-position unavailable message. Most Indian trading tools completely ignore NSE circuit limits. This is a real execution hazard addressed here.

### 4. Chart Context Layer ✅
SMA50/200 direction + overhead obstacle room check matches institutional trend-following overlays. The `MINIMUM_OBSTACLE_ROOM_ATR = 1.0` reject threshold and `COMFORTABLE_OBSTACLE_ROOM_ATR = 2.0` pass threshold are well-calibrated.

### 5. Floor Evidence in Breakout ✅
`floorDate`, `floorPrice`, `floorToBreakoutPct`, `floorToBreakoutAtr` in `AdaptiveBreakoutConfirmationEvidence` directly addresses the audit finding about extended breakouts.

---

## Part 2: Shortcomings & Recommendations

### 🔴 Finding 1: No Structural Stop-Loss / Invalidation Level

**The gap:** The engine tells you *when* to buy (`FRESH_BREAKOUT` + quality PASS) but never tells you *where your thesis breaks*. After a `FRESH_BREAKOUT`, the engine says `BREAKOUT_CONTINUATION` forever—even if the stock collapses back through the floor. A trader holding the position has no "thesis is dead" signal. The backtester uses a flat `stopLossPct` (default 5%), completely disconnected from the structure.

**Industry standard:**
- **Swing traders (Minervini SEPA):** Stop below the breakout candle's low, or below the ceiling's `anchorPrice` (resistance-turned-support). Typical risk: 3–5% from entry.
- **Volatility systems (Chandelier/Supertrend):** Stop at `entry - (1.5 × ATR)` to `entry - (2.5 × ATR)`. The Chandelier Exit standard is `highest_high - 3.0 × ATR₁₄`.
- **Wyckoff practitioners:** Stop below the last higher low (LPS) or below the Spring low.
- **Quantitative consensus:** `Trailing Stop = Retest Pivot Low - (1.0 to 1.5 × ATR₁₄)`.

**What exists vs what's needed:**

| Data point | Available? | Where? |
|-----------|-----------|--------|
| Floor price at breakout | ✅ | `breakoutEvidence.floorPrice` |
| Ceiling anchor (resistance-turned-support) | ✅ | `ceiling.anchorPrice` |
| Breakout candle low | ❌ Not surfaced | Available in rawSteps but not in evidence |
| ATR-based invalidation | ❌ Not computed | `latestAtr` exists but no invalidation formula |

**Recommendation:** 
- Add `STRUCTURE_INVALIDATED` to both `AdaptiveBreakoutDecision` and `AdaptiveBreakoutStatus` enums.
- Invalidation rule: when `breakoutDate != null`, the structure is invalidated if the close drops below `ceiling.anchorPrice - (0.25 × atrAtAnchor)`. The 0.25 ATR buffer below anchor gives noise tolerance.
- After invalidation, re-activate the ceiling (clear `breakoutDate`) so a re-breakout is possible.
- Add `invalidationPrice` to `AdaptiveBreakoutConfirmationEvidence`, `AdaptiveBreakoutEvaluation`, and `AdaptiveBreakoutScanRow`.
- In the backtester, add `STRUCTURE_INVALIDATED` as a third exit reason (alongside target and stop-loss). Whichever triggers first wins.
- In the frontend, add a "Today's Invalidations" filter within the existing "Already broken" tab—a "get out now" alert view showing: symbol, breakout date, breakout price, invalidation price, today's close, loss from entry.

> Without this, the backtester's flat 5% stop doesn't reflect how a trader would actually manage the position. Backtesting results may be misleading.

---

### 🟡 Finding 2: No "Pullback Entry" / Reclaim-Based Entry Alternative

**The gap:** The system only recognizes one entry type: buy at next-session open after `FRESH_BREAKOUT`. In the audit, 19 of 43 breakouts were already 15%+ extended from the floor.

**Industry standard — Minervini's "3-C Cheat" Entry:**
Professional breakout traders use two entry modes with staged position sizing:
1. **Aggressive entry (Full size):** Buy immediately on breakout close confirmation (what exists today).
2. **"Cheat" / Conservative entry (Tranche 1 — 40–50% size):** When price reclaims the base line (Line 1) after a failed breakout attempt, enter with tight risk below the shakeout low. This is Minervini's "Cup Completion Cheat"—it gives a lower cost basis and tighter stop before the stock clears the obvious cap at Line 2. Add the remaining 50–60% only when price clears the absolute cap (Line 2).

The logic: when a false breakout fails to cause a collapse, it proves sellers have no supply left. Trapped shorts who shorted the "failed breakout" must cover, creating fuel for the real move.

The engine already detects `CEILING_RECLAIM` (close reclaims the base line but stays below the failed-attempt cap). This is *exactly* the Minervini Cheat setup.

**Recommendation:**
- Track a `BREAKOUT_RETEST` event in the engine. When `breakoutDate != null` and the close dips back to within 0.5 ATR of `baseUpperBoundary` without closing below `anchorPrice`, emit this as a potential re-entry point.
- Surface `CEILING_RECLAIM` more prominently as a valid watch-list entry signal rather than just "watch only."

---

### 🟡 Finding 3: Floor Extension Not in Quality Rules

**The gap:** The audit showed 15.6% average floor-to-breakout, with 12 signals exceeding 20%. `floorToBreakoutPct` exists in the evidence but is not used as a quality gate.

**Industry standard — O'Neil / CANSLIM Base Depth Rules:**
- Flat Base: 10–15% depth (mildest contraction, highest continuation probability)
- Cup with Handle / VCP: 15–35% depth (standard institutional base)
- Deep Base / Turnaround: 35–50% depth (needs longer base duration)
- V-shaped recovery >50% depth with no pause: **failure rate exceeds 75%**

**Recommendation:** Add a 6th quality rule in `BreakoutDayQualityAnalyzer`:
- `floorToBreakoutPct ≤ 15%` → PASS (healthy base, not extended)
- `floorToBreakoutPct 15–25%` → WAIT (standard depth, watchable)
- `floorToBreakoutPct > 25%` → REJECT (likely V-shaped recovery or late-stage chase)

---

### 🟡 Finding 4: `CEILING_RECLAIM` Has No Accumulation Count

**The gap:** `detectCeilingReclaim` fires when the close crosses back above `baseUpperBoundary` but stays below `upperBoundary`. There's no tracking of how many times this reclaim happens. Multiple reclaims = strong demand absorption signal.

**Industry comparison — The "Failed Failure" Dynamic (Toby Crabel):**
When a breakout fails but price refuses to collapse, the sellers are exhausted. Each reclaim of the base line after the failure increases institutional conviction.

Professional execution on reclaims:
- The reclaim bar must show expanding volume (≥1.3–1.5× RVOL) and CLV ≥ 0.70
- The pullback between reclaims must occur on substantially lower volume (confirming no institutional distribution)
- Stop-loss anchored below the low of the recent shakeout/pullback (typically 3–5% risk)

**Recommendation:** Add `reclaimCount` to `ReplayCeiling`. Each `CEILING_RECLAIM` event increments it. When `reclaimCount >= 3`, the explanation notes "Multiple successful base-line reclaims suggest strong demand absorption at this level."

---

### 🟡 Finding 5: `mergeCeilings` Hides Descending Resistance

**The gap:** When an incoming ceiling's anchor is below the current `baseUpperBoundary`, `mergeCeilings` returns the old ceiling unchanged. A lower peak rejection (descending resistance / lower highs) is completely invisible. From a trader's perspective, descending resistance is bearish.

**Recommendation:** Minor for a breakout-buying system. Consider logging a `DESCENDING_RESISTANCE` observation in the explanation text so the trader can see the bearish pressure building. Does not affect the buy trigger itself.

---

### 🟡 Finding 6: Delivery Rule Baseline May Be Too Strict

**The gap:** The delivery rule requires 20 full prior delivery sessions. If even one session is missing delivery data, the entire rule becomes `UNAVAILABLE`.

**Industry comparison:** Most institutional volume analysis systems use a "best available" approach: if 20 sessions aren't available, fall back to 15, then 10.

**Recommendation:** Consider graceful degradation: if 20 sessions aren't available but ≥10 are, compute the ratio anyway and label as "partial baseline" rather than `UNAVAILABLE`.

---

### 🟢 Finding 7: Extension Rule Thresholds Are Sound

The extension rule (PASS 0–0.50 ATR, WAIT 0.51–1.0 ATR, REJECT >1.0 ATR from breakout line) aligns with the Minervini SEPA standard: never chase beyond +5% from the pivot or >1.0× ATR₁₄. No change needed.

### 🟢 Finding 8: Backtest Ambiguous-Candle Rule Is Conservative (Good)

The `STOP_LOSS_SAME_CANDLE` logic assumes stop is hit first when both target and stop are touched on the same daily candle. Correct conservative assumption. No change needed.

---

## Part 3: Industry Comparison Reference

### Breakout Day Candle Quality (Professional Thresholds)

| Metric | Your Threshold | Industry Standard | Assessment |
|--------|---------------|-------------------|------------|
| Close position (CLV) | PASS ≥80%, WAIT 60–79%, REJECT <60% | PASS ≥70% (institutional), ≥80% (aggressive) | Aligned ✅ |
| Volume vs 10D | PASS ≥1.5×, WAIT 1.0–1.49×, REJECT <1.0× | CANSLIM: ≥1.5–2.0× minimum | Aligned ✅ |
| Delivery vs 20D | PASS ≥1.25×, WAIT 1.0–1.24×, REJECT <1.0× | NSE-specific, no direct industry comp | Reasonable ✅ |
| Line clearance | PASS ≥0.10 ATR, WAIT 0–0.09, REJECT below | Industry: ≥0.20–0.25× ATR typical | Slightly lenient ⚠️ |
| Extension from line | PASS 0–0.50 ATR, WAIT 0.51–1.0, REJECT >1.0 | SEPA: never chase >+5% or >1.0× ATR | Aligned ✅ |
| Floor-to-breakout % | Not gated | CANSLIM: >50% depth V-shape = 75%+ failure | Missing ❌ |

### Breakout System Architecture Comparison

| Feature | Donchian (Turtle) | Keltner Channel | Bollinger Bands | Your System |
|---------|------------------|-----------------|-----------------|-------------|
| Resistance definition | N-day high | EMA + ATR band | SMA + 2σ band | ATR-buffered ceiling with failed-attempt tracking |
| Failed breakout handling | None | None | None | Two-line model (base + strict cap) ✅ |
| Volatility adaptation | None | ATR-smoothed | Standard deviation | ATR at anchor ✅ |
| Structural invalidation | None | None | None | Not yet ❌ |
| Trend filter | None standard | EMA centerline | SMA centerline | SMA 50/200 + direction ✅ |
| Circuit-lock awareness | N/A | N/A | N/A | `rangeLocked` ✅ |

---

## Part 4: Decided Outcomes (From Discussion)

### Tabled / Rejected
- **Stale ATR contraction:** Rejected. Backtests showed anchor-day ATR vs breakout-day ATR changes the line by <0.45%. POLYCAB demonstrated that the fixed line correctly waited for the stronger 17 Aug close while a tightened line would have triggered on the weak 13 Aug rejection.
- **"Volatility Ratchet" (minATR tracking):** Rejected for the same reason. The fixed anchor ATR acts as a noise filter.
- **Quality gate in backtest:** Intentionally kept separate. Quality validation is done independently because market-new information will be incorporated separately.

### Approved for Implementation
1. **Structure Invalidation** (Finding 1) — Priority #1
2. **Ceiling Reclaim Count** (Finding 4) — Priority #2
3. **Floor Extension Quality Rule** (Finding 3) — Priority #3
4. **Ceiling Reclaim as Watch Signal** (Finding 2, partial) — Priority #4

---

## Part 5: Implementation Plan Summary

### Phase 1: Structure Invalidation
1. Add `STRUCTURE_INVALIDATED` to decision/status enums + `invalidationPrice` to models
2. Add `detectStructureInvalidation()` to engine: fires when post-breakout close < `anchorPrice - 0.25 ATR`
3. Re-activate ceiling on invalidation (clear `breakoutDate`, allow re-breakout)
4. Add `STRUCTURE_INVALIDATED` as third backtest exit reason
5. Surface `invalidationPrice` in scan response
6. Frontend: "Today's Invalidations" view within "Already broken" tab

### Phase 2: Medium Priority
1. Add `reclaimCount` to `ReplayCeiling` and public ceiling model
2. Add floor extension quality rule (PASS ≤15%, WAIT 15–25%, REJECT >25%)
3. Enrich `CEILING_RECLAIM` explanation when `reclaimCount >= 3`

### Phase 3: Test & Verify
1. All existing tests pass
2. Validate against core watchlist audit stocks (NETWEB, CGPOWER, GVT&D, HFCL, MTARTECH, POLYCAB, STLTECH, KRN)
