# Breakout Forensics — Chartink Dashboard Strategy

## Context

We are shifting research method while keeping the same core buy-side intent. The work is split into **filters**, not one monolithic buy signal.

| Old path | New path |
|----------|----------|
| Forward Wyckoff rules → scan → wait for breakout | Outcome-first forensics → rewind → build stock profiles → cluster archetypes |
| Prescriptive footprint model | Empirical signature discovery |

Forensics (stock-by-stock review via Kite MCP + delivery data) validates rules. **Chartink runs Filter 1 daily.**

---

## What “breakout” means (product intent)

**Breakout is not primarily a price % move.**

Filter 1 answers: **Has the accumulation phase ended with a volume/delivery footprint shock — the handoff toward Phase D?**

| Layer | Meaning |
|-------|---------|
| **Primary** | Volume anomaly + delivery footprint = participation shock at end of base |
| **Secondary** | Price move — ranking / confidence, **not** a hard gate |
| **Goal** | Enter when accumulation **ends**, before or as markup begins — not chase a +7% candle |

Wyckoff framing: we want the **golden entry** at the C → D transition. Huge volume + delivery with a **small or flat price result** can be a *better* candidate than a same-day rip — it may mean absorption completed, not a missed signal.

**Price fail (distribution trap):** stock **breaks down hard** on the volume day despite the footprint — not “close didn’t go +5%.”

### Wyckoff priority view

In Wyckoff terms, the best breakouts are not simply the ones with the biggest green candle or the highest raw volume multiple. The highest-priority breakouts are the ones that emerge from a **real cause-building process**:

- a visible accumulation structure
- a quieting of supply and participation during the base
- then a decisive demand release as the stock exits that cause

That means **pre-breakout dry-up is a positive sign**, not just a volume oddity. It often suggests supply has already been absorbed and weak hands are largely gone. When multiple stocks break out on the same day, priority should go to the names where the breakout is coming **out of a proper accumulation cause**, especially when volume had clearly dried up before expansion.

---

## Product funnel (filters)

Filter 1 is **not** a final buy. It surfaces names where **accumulation likely ended today** for deeper review.

```
Filter 1 — Phase transition catch (Chartink)  →  volume/delivery footprint shock today
Filter 2 — Wyckoff / accumulation review      →  was there a real base? prior clues?
Filter 3 — Fundamentals                       →  RoCE, profits, quality, sector
Filter 4 — Final buy decision                 →  entry timing, size, risk
```

| Filter | Question | Tool |
|--------|----------|------|
| **1** | Did accumulation **end today** on a volume/delivery anomaly? | Chartink dashboard |
| **2** | Was the prior phase pure accumulation (no transition hint)? | TradingTool / manual |
| **3** | Is this a fundamentally strong stock? | External / future |
| **4** | Do I buy now? | Discretionary |

**Accumulation-phase days must not fire Filter 1.** High delivery % on a flat day with normal volume (e.g. LXCHEM 19 Jun) is Filter 2 material only — base still in progress.

---

## Product Decision: Chartink per use case

**Chartink is the primary daily screening surface for Filter 1.** Separate dashboards may exist for other use cases (outcome cohort research, context gates), but **Filter 1 is the main production screener.**

### Operating model

```
Filter 1 (Chartink)  ──daily CSV / review──►  shortlist for Filter 2
Filter 2 (TradingTool / manual)  ────────────►  accumulation validated
Filter 3 (fundamentals)  ────────────────────►  quality gate
Filter 4  ───────────────────────────────────►  trade decision

Outcome cohort (10–20% movers)  ──forensics──►  profile store / archetype tags
```

Existing Phase C CSV upload flow in TradingTool stays valid for Filter 2-style watchlist work.

---

## Filter 1 — signal stack (v1)

Two time windows. **Primary vs secondary** gates are explicit.

### Prior window (D-10 to D-1) — accumulation in progress

| Pillar | Rule | Role |
|--------|------|------|
| **Flat / sideways price** | Low drift (tune: 5d or 10d window) | Base, not markup |
| **ATR / range** | Compressed vs stock baseline | Volatility squeeze |
| **ROC** | Near flat preferred; downtrend bases allowed | Context, not always hard gate |

No volume/delivery shock in prior window. Quiet delivery on flat days = accumulation, not Filter 1.

### Signal day (D) — accumulation ends / Phase D handoff

**Primary (required):**

| Signal | Rule |
|--------|------|
| **Volume anomaly** | Today volume > prior 10d max (or stock-relative multiple) |
| **Delivery footprint** | Delivery qty spike vs prior window and/or extreme delivery % at lows |

**Secondary (ranking / confidence, not hard gate):**

| Signal | Rule |
|--------|------|
| **Price** | Modest up, flat, or small down **without breakdown** on huge volume (absorption OK) |
| **ROC / 200 SMA** | Context for Filter 2 — not always Filter 1 gate |
| **Same-day price expansion** | Bonus confirmation (`FOOTPRINT_PLUS_PRICE`), not required |

**Critical rules:**

- Do **not** require +3% / +5% close move for Filter 1.
- Do **not** require flat price on signal day (but flat price on footprint day is the **preferred golden entry**).
- ROC flat-band applies to **prior window** when used; signal day can be anything.
- Filter 1 fires on **transition day D** — not on early delivery-only clues (Filter 2).
- Multi-day campaigns are valid: accumulation may end on the **first volume breach day while price is still flat** (see `STAGED_FOOTPRINT`).

---

## Archetype tags (Filter 1 expressions)

| Tag | Character | Golden entry | Reference |
|-----|-----------|--------------|-----------|
| **`STAGED_FOOTPRINT`** | 2–3 day escalation: delivery → vol breach (flat price) → markup | **D-1 of markup** (first vol shock, price still flat) | **RBA** (preferred) |
| `FOOTPRINT_TRANSITION` | Single-day vol + delivery shock; price quiet | Signal day | HMAAGRO |
| `FOOTPRINT_PLUS_PRICE` | Footprint + same-day markup in one session | Signal day (later) | LXCHEM, TEXINFRA, KDDL, GPTINFRA |

**Product preference:** `STAGED_FOOTPRINT` is the **best reference archetype** so far. It shows the full Wyckoff handoff across sessions — delivery conviction building, then volume breach with flat price (golden entry), then markup confirmation. Cleaner than single-day `FOOTPRINT_PLUS_PRICE` (+7% same day) and richer than one-day `FOOTPRINT_TRANSITION`.

### Priority breakout principle

If several names fire Filter 1 together, we should not treat them as equal. **Priority breakout candidates** are the ones with:

1. a cleaner accumulation base
2. a stronger pre-breakout volume dry-up
3. a clearer participation shock on the breakout day
4. a more orderly Wyckoff C → D handoff

This means a stock with a smaller but cleaner breakout can deserve **higher priority** than a noisier stock showing a larger raw volume multiple.

---

## Chartink dashboards

### Dashboard 1 — `FILTER_1_PHASE_TRANSITION` (primary production screener)

**Intent:** Stocks in a base where **accumulation ends today** on volume + delivery footprint.

| Layer | When | Chartink-style logic |
|-------|------|----------------------|
| Base | D-10 to D-1 | Price drift minimal (5d or 10d); ATR compressed |
| **Primary** | D (today) | Volume > 10d max volume |
| **Primary** | D (today) | Delivery qty > prior 10d max delivery qty (when data available in Chartink/export) |
| Secondary | D (today) | Close % not below breakdown threshold (e.g. > -3% on volume day) |
| Optional rank | D (today) | Close % > 0 boosts rank; not required |

**Review question:** Did accumulation **end today** with a participation shock?

**Not in scope:** Fundamentals, final buy sizing.

---

### Dashboard 1b — `FILTER_1_STAGED_FOOTPRINT` (preferred golden-entry screener)

**Intent:** Catch the **first volume breach day while price is still flat** — the staged C → D handoff. Reference pattern: **RBA 18 Jun 2026**.

| Layer | When | Chartink-style logic |
|-------|------|----------------------|
| Base | D-10 to D-3 | Price drift minimal; ATR compressed; no vol shock |
| Filter 2 clue (optional) | D-2 | Elevated delivery % or delivery qty on flat price |
| **Golden entry** | D-1 | Volume > 10d max **AND** close change <= +2% **AND** delivery footprint |
| Confirmation | D | Larger volume + price markup (rank boost, not required for first alert) |

**Review question:** Did participation shock arrive **before** visible markup?

Use alongside Dashboard 1. When both fire on the same symbol on consecutive days, treat as high-confidence `STAGED_FOOTPRINT`.

---

### Dashboard 2 — `QUIET_FOOTPRINT` (Filter 2 / research)

**Intent:** Accumulation clues before transition — volume or delivery expands while price stays quiet.

| Layer | Chartink-style logic |
|-------|----------------------|
| D-1 or D-2 | Elevated delivery % or delivery qty on flat price |
| D-1 or D-2 | Daily close change <= +2% |
| Context | ATR compressed |

Does not replace Filter 1.

---

### Dashboard 3 — `OUTCOME_COHORT` (backward research)

**Intent:** Stocks that moved 10–20% with volume — forensic rewind, not Filter 1.

---

### Dashboard 4 — `EXTENDED_BASE` (shared context filter)

Optional 200 SMA / ROC columns for Filter 2 ranking.

---

## What lives where

| Concern | Chartink | TradingTool |
|---------|----------|-------------|
| Filter 1 daily scan | ✓ Dashboard 1 | |
| Volume, ATR, ROC | ✓ | |
| Delivery qty / % on signal day | ✓ export / manual | ✓ bhavcopy |
| CSV → watchlist | ✓ export | ✓ upload |
| Filter 2 accumulation | | ✓ |
| Fundamentals | | Filter 3 |
| Kite forensics | | Agent / MCP review |

---

## Validated case studies

### RBA — reference case (`STAGED_FOOTPRINT`) — preferred example

**Restaurant Brands Asia Ltd. · NSE:RBA · token 382465**

This is the **best reference case** in the library so far. It shows the full accumulation-to-markup handoff across three sessions better than single-day `FOOTPRINT_PLUS_PRICE` or one-day `FOOTPRINT_TRANSITION`.

| Field | Value |
|-------|-------|
| Filter | **1** — staged phase transition |
| Archetype | **`STAGED_FOOTPRINT`** (reference) |
| Pre-base (Jun 10–16) | **+0.9%** drift, avg vol ~2.9M, range ~1.2% — tight flat base |
| **17 Jun (D-2)** | Vol 3.70M, delivery **83.0%** / **3,067,358** dq, VWAP 69.10, close +0.31% — **Filter 2** delivery conviction; vol not yet > 10d max |
| **18 Jun (golden entry ★)** | Vol **9.89M** (**2.3×** 10d max), delivery **76.1%** / **7,528,671** dq, VWAP 69.64, close **+0.94%** — **accumulation ended; price still flat** |
| **19 Jun (confirmation)** | Vol **81.89M** (**8.3×** vs 18 Jun), delivery **34.8%** / **28,503,206** dq, VWAP 73.76, close **+5.71%** — markup begins |
| Post move | 22 Jun +8.16% (68.7M vol) — second markup leg |
| 200 SMA context | +2.4% above on 17 Jun → +9.4% on 19 Jun — reasonable at entry |

**Three-day sequence (canonical pattern):**

```
17 Jun:  delivery conviction (83%)     → price flat     → Filter 2 clue
18 Jun:  volume breach + delivery       → price STILL flat → Filter 1 golden entry
19 Jun:  volume nuke + price markup     → confirmation
22 Jun:  continuation leg
```

**Why this beats similar examples:**

| vs | RBA advantage |
|----|----------------|
| TEXINFRA / LXCHEM / KDDL (`FOOTPRINT_PLUS_PRICE`) | Separates **footprint** from **markup** across days — entry before +7% candle |
| HMAAGRO (`FOOTPRINT_TRANSITION`) | Shows **escalation** (delivery → vol → price) in a tight base, not a distressed downtrend |
| GPTINFRA | Clearer multi-session read; RBA 18 Jun is unambiguous golden entry |

**Chartink:** Dashboard 1b (`FILTER_1_STAGED_FOOTPRINT`) should be tuned from this case first.

---

### HMAAGRO — Filter 1 on 22 Jun 2026 (`FOOTPRINT_TRANSITION`)

| Field | Value |
|-------|-------|
| Filter | **1** — accumulation ended; footprint transition (preferred Wyckoff entry) |
| Signal date | 2026-06-22 |
| Pre-base (Jun 10–19) | -1.9% drift, avg range ~3.4%, sideways at lows |
| D-1 (19 Jun) | Vol 332k, delivery **75.9%** / 251,879 dq — accumulation in progress |
| Signal day | Vol **7.4×** 10d max (3.32M), delivery **87.5%** / **2,906,466** dq |
| Price on D | **-1.08%** close — small move; **no breakdown** on huge volume (absorption) |
| Context | Below 200 SMA (~-19%), downtrend base at lows — Filter 2 quality review |
| Post move | Heavy vol continued; Jun 23–24 chop |
| Archetype | **`FOOTPRINT_TRANSITION`** — primary signal is vol + delivery, not price rip |

**Passes Filter 1 under footprint-first rules.** Secondary reference for single-day `FOOTPRINT_TRANSITION`. Prefer **RBA** as the staged reference.

---

### GPTINFRA — Filter 1 on 18 Jun 2026 (`FOOTPRINT_PLUS_PRICE`)

| Field | Value |
|-------|-------|
| Signal date | 2026-06-18 |
| Pre-base | 12d flat (-0.17%), avg range ~2.5% |
| D-1 | Vol 51,380 (60d low) — dry-up |
| Signal day | Vol 3.07× 10d max, close **+5.43%** |
| Post move | +22.6% to peak ~4 sessions later |
| Archetype | **`FOOTPRINT_PLUS_PRICE`** / early SOS |

---

### LXCHEM — Filter 1 on 22 Jun 2026 (`FOOTPRINT_PLUS_PRICE`)

| Field | Value |
|-------|-------|
| Signal date | 2026-06-22 |
| Pre-base (Jun 10–19) | +1.6% drift, no transition hint |
| D-1 (19 Jun) | Delivery 44.8% / 191,748 dq — accumulation only |
| Signal day | Vol **12.7×** 10d max, close **+6.9%**, dq 1.28M at 13.4% |
| Baseline caveat | Exclude Jun 3 (46.6M vol event) from history windows |
| Archetype | **`FOOTPRINT_PLUS_PRICE`** |

---

### TEXINFRA — Filter 1 on 22 Jun 2026 (`FOOTPRINT_PLUS_PRICE`)

| Field | Value |
|-------|-------|
| Signal date | 2026-06-22 |
| Pre-base | Jun 15–19 drift **+1.2%** (5d); Jun 10–19 +3.5% (10d borderline) |
| D-1 (19 Jun) | Vol 94k, delivery **64.5%** / 60,744 dq — accumulation only |
| Signal day | Vol **8.0×** 10d max, close **+6.9%**, dq **745,831** at **45.9%** |
| Post move | Held 2 sessions |
| Archetype | **`FOOTPRINT_PLUS_PRICE`** — same-day footprint + markup; see **RBA** for staged version |

---

### KDDL — Filter 1 on 22 Jun 2026 (`FOOTPRINT_PLUS_PRICE`)

| Field | Value |
|-------|-------|
| Signal date | 2026-06-22 |
| Pre-base | Low-vol grind (+3.2% drift); avg vol ~17k |
| D-1 (19 Jun) | Delivery **57.7%** / 14,414 dq — accumulation only |
| Signal day | Vol **5.2×** 10d max, close **+6.9%**, dq 61,746 at 30.8% |
| Filter 2 flag | Already **+21%** above 200 SMA before signal — extended |
| Archetype | **`FOOTPRINT_PLUS_PRICE`** — repeat of TEXINFRA/LXCHEM cluster |

---

### DELHIVERY — 19 Jun 2026 is **not** the breakout day; likely Filter 2 clue only

**Delhivery Ltd. · NSE:DELHIVERY · token 2457345**

This is an important blue-chip contrast case. It shows why **delivery participation alone is not enough** for Filter 1, especially in larger-cap names where the true transition often appears as a later **volume expansion day**.

| Field | Value |
|-------|-------|
| Study date | 2026-06-19 |
| User-provided day stats | Delivery **54.0%** / **947,317** dq, VWAP **460.17**, traded qty **1,754,855** |
| Price on 19 Jun | Close **461.10**, **+0.41%** vs 18 Jun, range ~**1.98%** |
| Volume on 19 Jun | **1.72M** shares |
| Vs prior 10d avg vol | **0.79×** |
| Vs prior 10d max vol | **0.39×** |
| Volume Z-score vs prior 10d | **-0.42** |
| Read | **Not Filter 1** — no participation shock yet |
| Best label | `QUIET_FOOTPRINT` / Filter 2 watch, if delivery is judged meaningful |

**Interpretation:** 19 Jun shows **respectable delivery participation**, but **no volume anomaly at all**. For this framework, that means **accumulation may still be in progress**, not that accumulation has ended. The first clear transition day appears to be **22 Jun 2026**, when DELHIVERY traded **12.51M** shares, or **5.62×** its prior 10d average and **2.85×** its prior 10d max, with a **+4.92%** close expansion.

**Working rule from this case:** in blue-chip / higher-liquidity names, even a **"smaller" multiple like 3× to 6×** can be a serious transition event. We should not assume the best signals must always be **20× to 50×**. Those extreme multiples may say more about a stock's normally thin baseline than about superior institutional quality.

---

### EIEL — dry-up amplified breakout sequence on 19 Jun and 22 Jun 2026

**Enviro Infra Engineers Ltd. · NSE:EIEL · token 6966529**

This case strongly supports the idea that **breakout intensity is partly a function of how hard volume dried up before the move**. A large multiple does not always mean stronger institutional demand by itself; sometimes it means demand arrived **after an unusually dead base**, which makes the ratio explode.

| Field | 19 Jun 2026 | 22 Jun 2026 |
|-------|-------------|-------------|
| User delivery data | **25.9%** / **1,744,534** dq | **15.8%** / **3,698,338** dq |
| VWAP | **206.53** | **225.12** |
| Traded qty | **6,746,097** | **23,400,539** |
| Kite volume | **6.45M** | **22.50M** |
| Close move | **+5.70%** | **+9.25%** |
| Vs prior 10d avg vol | **8.03×** | **16.20×** |
| Vs prior 10d median vol | **8.91×** | **28.39×** |
| Vs prior 10d max vol | **4.28×** | **3.49×** |
| Read | Initial breakout / SOS | Follow-through / expansion |

**Dry-up context before 19 Jun:** from **2 Jun to 18 Jun 2026**, EIEL averaged only about **0.80M** shares/day, with a median near **0.75M** and a low of just **493,908** shares. That is a clear low-activity basin. Against that backdrop, **19 Jun** is a real breakout day, but its **8× to 9×** multiple is also being **amplified by pre-breakout dryness**.

**Important nuance:** the best way to read this is not just "22 Jun is 16×, so it is automatically better than 19 Jun." By the time 22 Jun printed, the baseline had already been lifted by the 19 Jun breakout. That is why 22 Jun is only **3.49×** the prior 10d max even though it is **16.2×** the prior 10d average. This suggests:

- **Average / median multiples** capture how dead the base was.
- **Max-volume multiple** better captures whether the day truly exceeded prior participation shocks.
- A strong setup may therefore need **both**:
  1. clear pre-breakout dry-up
  2. decisive breakout-day participation beyond the base high-water mark

**Working rule from this case:** treat **dry-up intensity** as a separate pillar from **breakout intensity**. A huge `10×+` or `20×+` multiple can be partly a **dry-up amplifier**, not just a cleaner breakout. That does not weaken the signal; it changes what the number means.

---

### Case comparison

| | **RBA** ★ | HMAAGRO | TEXINFRA | LXCHEM | KDDL | DELHIVERY |
|---|-----------|---------|----------|--------|------|-----------|
| Golden entry | **18 Jun** | 22 Jun | 22 Jun | 22 Jun | 22 Jun | **22 Jun** |
| Archetype | **`STAGED_FOOTPRINT`** | `FOOTPRINT_TRANSITION` | `FOOTPRINT_PLUS_PRICE` | `FOOTPRINT_PLUS_PRICE` | `FOOTPRINT_PLUS_PRICE` | `BLUECHIP_TRANSITION` candidate |
| Entry price move | **+0.9%** | -1.1% | +6.9% | +6.9% | +6.9% | **+4.9%** |
| Vol at entry | **2.3×** 10d max | 7.4× | 8.0× | 12.7× | 5.2× | **2.85×** |
| Del % at entry | **76.1%** | 87.5% | 45.9% | 13.4% | 30.8% | Pending delivery data |
| Base quality | Tight (+0.9%) | Downtrend lows | Tight flat | Mild drift | Grind up | Tight to mild drift |
| Reference rank | **#1 preferred** | #2 single-day | Cluster | Cluster | Cluster | Contrast case |

---

## V1 signal pillars (summary)

| Pillar | Prior window (base) | Signal day (transition) |
|--------|---------------------|-------------------------|
| Volume anomaly | Not required | **Primary** — > 10d max |
| Delivery footprint | Not required | **Primary** — qty spike / extreme % |
| Flat / sideways price | **Required** (tune window) | Not required |
| ATR compressed | **Required** | Expansion OK |
| ROC | Context | Context |
| Price % move | Not required | **Secondary** — not primary gate |
| Volume dry-up | **Priority signal** — tighter is better | Not required |

### Wyckoff ranking lens

Filter 1 still answers a binary question: **did accumulation likely end today?** But when multiple stocks pass, ranking should be Wyckoff-first:

| Rank pillar | Why it matters |
|-------------|----------------|
| **Base quality** | Better cause usually means cleaner markup potential |
| **Dry-up intensity** | Suggests supply exhaustion / absorption before breakout |
| **Participation shock** | Confirms demand arrived decisively |
| **Price behavior on breakout** | Flat or modest move can be ideal; huge move is not automatically better |

In other words:

`best breakout = strong cause + strong dry-up + decisive demand release`

---

## Open items

- Dry-up metric: define a simple pre-breakout dryness score using prior 10d avg volume, prior 10d median volume, and lowest 3-day average volume.
- Volume-intensity ranking: create a **tier system** (`2-3×`, `3-5×`, `5-10×`, `10×+`) and test whether large caps need lower trigger multiples than small caps.
- Composite interpretation rule: decide how to combine **dry-up intensity** with **breakout intensity** so we do not overrate huge multipliers caused mainly by a dead baseline.
- Priority ranking model: formalize a small scorecard with **base quality**, **dry-up intensity**, **breakout participation**, and **breakout quality**.
- DELHIVERY 22 Jun delivery data: collect and confirm whether the true breakout day also had a strong delivery footprint or was primarily a raw volume-led transition.
- Exact Chartink syntax for Dashboard 1b: vol > 10d max **AND** close <= +2% **AND** delivery footprint (tune from RBA).
- Alert chaining: Dashboard 1b signal on D-1 → Dashboard 1 confirmation on D.
- Base drift window: 5d vs 10d (TEXINFRA needs 5d for flat pass).
- Breakdown threshold on signal day (e.g. reject if close < -5% on footprint day).
- Event-day exclusion (e.g. LXCHEM Jun 3).
- Distribution vs absorption disambiguation in Filter 2 when below 200 SMA.

---

## Next step

Continue stock-by-stock forensics. Tag each case against archetypes; **`STAGED_FOOTPRINT` (RBA pattern) is the preferred reference.** Tune Chartink Dashboard 1b first, then Dashboard 1 for confirmation-day markup.
