# Project Composite: Wyckoff Institutional Bloodhound

## 1. Project Overview
**Target Market:** National Stock Exchange of India (NSE)  
**Core Utility:** Anomaly-First (Backward) Stock Discovery Pipeline focusing exclusively on Wyckoff Stage 1 Accumulation Floor Detection.  
**Core Philosophy:** Markets are driven by the "Composite Man." Instead of forward-scanning for chart patterns, the system performs a brute-force backward sweep across the entire market to find mathematical footprints of supply exhaustion first, before evaluating the structural price context.  
**Status:** Architecture and Strategy Definition (Day 6 Updates + Edge Cases)

## 2. Target Universe & Data Boundaries
**Active Scanning Universe (Tracks A & B):**
- NIFTY 50
- NIFTY 100
- NIFTY MIDCAP 50
- NIFTY MIDCAP 150
- NIFTY SMALLCAP 50
- NIFTY SMALLCAP 250
- NIFTY MICROCAP 250
- *Nano-caps included implicitly via threshold logic*

**Curated Universe (Track C):** 
Manual static list of 50-100 High-Growth / Sector Leader stocks (avoids the "Denominator Flaw" where retail churn masks delivery percentages).

## 3. Data Pipeline & Backend
- **Backend Stack:** Kotlin utilizing the `ta4j` library for technical indicator computation.
- **Ingestion Strategy:** Scrape the master NIFTY 500 and NIFTY MICROCAP 250 Bhavcopies + Delivery reports daily.
- **Schedule:** Daily at 18:30 IST.

## 4. The 3-Phase Pipeline Architecture
The system avoids the "Moving Average Drag" flaw (where sustained institutional buying blinds standard screeners) by utilizing a strict 3-Phase pipeline combining historical density and Z-Scores.

### Phase 1: Primary Anomaly Scanners (Backward Sweep)
Scans the universe daily to isolate active institutional footprints using Cap-Adaptive metrics.
- **Historical Volume Shock Anchors (Vectorized via ta4j):**
  - **HVQ** (Highest Volume in Quarter): Current volume is the maximum of the rolling 60-day window.
  - **HVY** (Highest Volume in Year): Current volume is the maximum of the rolling 250-day window.
  - **HVE** (Highest Volume Ever): Current volume is the all-time maximum.
  - **LVQ** (Lowest Volume in Quarter): Current volume is the minimum of the rolling 60-day window.
  - **LVY** (Lowest Volume in Year): Current volume is the minimum of the rolling 250-day window.
  - **Institutional Bias:** `Is_Blue` (Close > VWAP), `Is_Red` (Close < VWAP).

- **Track A: The Golden Zone (Dual-Confirmation Matrix with Cap-Adaptive Thresholds)**
  - *Cap-Adaptive Delivery Check:*
    - Large/Mid-Caps: > 55%
    - Small-Caps: > 70%
    - Micro-Caps: > 85%
    - Nano-Caps: > 92%
  - *Historical Density:* The Cap-Adaptive threshold must be breached ≥ 4 times within a rolling 15-day window.
  - *Math Shock:* Delivery Volume Z-Score ≥ 2.0 (calculated against a 60-day baseline) to provide double confirmation alongside the rolling density.
  - *Absorption Check:* Daily Price Spread < 20-Day Average Spread (verifies limit order pinning).

- **Track C: The High-Growth Exceptions**
  - *Rule:* Scans the manual 50-100 list exclusively for absolute volume shocks near major structural support, ignoring delivery percentage entirely to bypass the Denominator Flaw.

### Phase 2: The 4-Brick Context Filter (The Base Check)
Validates that the anomaly from Phase 1 is occurring in a true Phase A/B "Dead Base" and not a Phase 4 Markdown (falling knife).
- **Brick 1 & 2:** Handled by Phase 1 Delivery/Volume anomalies.
- **Brick 3 (Long-term Gravity):** `Distance_200DMA` is strictly between -5.0% and +5.0%.
- **Brick 4 (Short-term Stability):** `ROC_20` sits dead-flat between -5.0% and +5.0% (Sideways Box).

### Phase 3: Capital Deployment (Execution Triggers)
**Rule:** Never buy the raw anomaly to avoid the "Time Trap" (dead capital during Phase B). Capital is deployed strictly via historical volume shock logic:

- **The "Calm Before the Storm" Scanner (Wyckoff Phase C - Spring/Test):**
  - *Logic:* (`LVY` is True OR `LVQ` is True) AND `ROC_20` is between -5.0% and +5.0%.
  - *Goal:* Catch assets when supply is completely exhausted (absolute zero retail activity), creating the ultimate low-risk floor.

- **The "Institutional Launch" Scanner (Wyckoff Phase D - Breakout/SOS):**
  - *Logic:* (`HVY` is True OR `HVQ` is True) AND `Is_Blue` is True.
  - *Goal:* Instant execution when momentum algorithms and big money aggressively trigger the markup out of the base.

## 5. Structural Traps & Edge Cases Mitigated
- **The Distribution Trap (The 52-Week High Flaw):** Extreme delivery spikes near a 52-week high represent a Buying Climax (institutions distributing into retail FOMO). Phase 2 (Brick 3: 200 DMA Gravity constraint) mathematically disqualifies these distribution events from being flagged as accumulation.
- **The Denominator Flaw (The NETWEB Exception):** High-growth stocks suffer from massive intraday retail churn, artificially crushing delivery percentages to 20-30% and hiding institutional buying. Track C completely bypasses this by dropping delivery % requirements for the curated list, focusing solely on absolute volume shocks.
- **The "Bad News" Mirage (The Swiggy Scenario):** A stock hitting a 52-week low on bad news paired with a massive delivery spike is the prime footprint of Phase A capitulation. Institutions require this engineered retail panic to vacuum up the float quietly. The pipeline actively targets this setup rather than filtering it out.

## 6. Execution Rules & Backtesting Framework
- **Signal Generation:** Phase 1 anomalies and Phase 2 context are verified post-market on Day T.
- **Watchlist Staging:** Validated tickers are moved to an active monitoring state awaiting Phase 3 execution triggers.
- **Entry Order:** Triggered on Phase C exhaustion or Phase D launch confirmations.
- **Profit Target/Stop Loss:** To be structurally defined based on asset velocity.
- **Bucket Tracking:**
  - *Closed Bucket:* Target/Stop reached.
  - *Open/Floating Bucket:* Tracking total elapsed holding duration and floating PnL.

## 7. Open Issues & Pending Decisions
- **Stop-Loss / Take-Profit Architecture:** Structure the exit logic differently for high-velocity assets versus slow-grinding structural assets.

## 8. Immediate Next Steps
1. Implement custom `ta4j` logic in Kotlin for the Cap-Adaptive Thresholds, 60-day Z-Score, and 15-day rolling frequency logic.
2. Backtest the combined "Track A: Golden Zone" logic on historical data to evaluate the signal-to-noise ratio.
3. Build and manually tag the initial static universe of 50-100 High-Growth / Sector Leader stocks for Track C.
