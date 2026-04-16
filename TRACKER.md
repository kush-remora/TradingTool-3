# Project Tracker

This file tracks active, pending, and completed features. 

---

## 🚀 Active Features

### 1. Watchlist Refactoring
- **Status:** In Progress
- **Description:** Organizing the watchlist backend and improving UI components for better performance and maintainability.
- **Related Docs:** 
    - `docs/features/watchlist/schema.md`
    - `docs/features/watchlist/api.md`
    - `docs/features/watchlist/dashboard-plan.md`

### 2. Remora Strategy
- **Status:** In Progress
- **Description:** A specific trading strategy focusing on high-momentum stocks.
- **Related Docs:**
    - `docs/strategies/remora-backend.md`

---

## ✅ Completed Features
* **Watchlist Refactoring** (2026-04-06): Transitioned to pre-defined tagging system from `watchlist_config.json`.
* **RSI Momentum V1** (2026-04-14): Daily snapshots, history, backtesting with buffer exits, and lifecycle analytics.
* **RSI Momentum V1.1** (2026-04-14): Stateful Rank-Based Backtesting and Multi-Symbol comparison view.
* **RSI Safe Sniper** (2026-04-16): Fully configurable "Safe" variant with on-demand "Sniper" backtester (10% Target / 3% SL logic).
    - Configurable SMA20 Extension, Rank Filter, and Volatility via `rsi_momentum_config.json`.
    - Three entry logics: Leader, Jumper (Rank Improvement), and Hybrid.
    - Integrated "Explainability" links to historical snapshots, Kite, and Groww.

### 3. Strategy UI Separation
- **Status:** Completed ✅
- **Description:** Built distinct UI screens for "RSI Momentum Safe" and "Weekly Swing Accumulation" strategies.
- **Key Components:**
  - `RsiMomentumSafePage`: Focused on Rank Improvement and SMA20 extension.
  - `WeeklySwingPage`: Focused on VCP tightness and Monday Bias strike rate.
  - Backend enhancements for Rank Improvement and VCP metrics.

---

## 📝 Pending / Idea Backlog
- [ ] Weekly Seasonality Profiler (`docs/strategies/weekly-seasonality.md`)
- [ ] Telegram Bot Integration (`docs/features/telegram-bot.md`)
- [ ] Kotlin Migration (`docs/guides/kotlin-migration.md`)
