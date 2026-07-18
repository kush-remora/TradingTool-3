# Hybrid Validation Pipeline — Implementation Start

The source specification is `.claude/requirements/strategies/52w-momentum/hybrid-validation-pipeline.md`. Its later decisions are captured in the dual-conviction Q&A and compiled HLD: one manually uploaded, complete daily evidence batch supports two independently runnable workflows—Forward High Conviction and Backward Forensics. Raw evidence and linkage must remain inspectable; execution is explicit, stepwise, and rerunnable. Missing local delivery is visible but non-blocking; missing Kite candle history stops the affected run.

The first delivery slice is the shared evidence ledger. It will define the finite set of required external sources, validate a complete batch atomically, persist raw symbol/date/source events, and expose the typed service boundary needed by later evidence browsing and both workflows. Shape classification, clustering, delivery analysis, breakout calculation, and new UI screens are deliberately deferred until that evidence foundation is proven. Validation will cover batch completeness, malformed rows, deduplication, and atomic persistence semantics.

Before that manifest can be implemented, the source list must be reconciled. The original hybrid document defines 11 files (6 accumulation, 3 dry-up, 1 ignition, 1 momentum) and does not include Groww. The later Q&A/HLD requires Groww plus `LVQ`, `LV100`, `LVY`, accumulation, ignition, and Phase-D/momentum sources, and one Q&A answer describes cap-split ignition files. The implementation will use the later Q&A/HLD once the exact required file set is confirmed.

## Current simplified source discussion

Kush currently prepares four cap-specific accumulation files using `refined-experiment-2026-07-18.md`, plus Phase D markup, volume-without-price-move, fresh breakout, a 52-week-high-labelled trend scan, delivery-percentage shock, and Groww Volume Shocker daily data. Initial recommendation: build v1 around the four accumulation files, Phase D markup, and delivery-percentage shock. Groww remains optional review evidence. The three remaining Chartink scans are deferred because they overlap with the retained inputs or identify moves too late for the initial conviction-building workflow. This is a recommendation awaiting Kush's confirmation, not a final manifest.

## Curated watchlist priority

Personal curated watchlists remain separate `index_constituents` memberships, alongside a stock's broad Nifty universe membership. When a stock has an Accumulation, Phase D, Fresh Breakout, or 52-week-breakout event, the future dashboard must show all applicable watchlist names and sort that stock above non-watchlist candidates. Watchlist membership is a visibility and priority modifier only; it does not create a trading signal.

## Implemented Chartink evidence slice — 2026-07-18

The simplified v1 now has exactly seven fixed uploads: four cap-specific Accumulation histories, plus Phase D, T2 High, and Fresh Breakout cash scans. Each CSV must have `Date`, `Symbol`, `Marketcapname`, and `Sector`. The importer validates before replacing anything, removes duplicate date/symbol rows, and only stores a symbol when it has one of the four configured Nifty universe memberships. A replacement is deliberately broad: one Accumulation universe, or the complete cash-scan source.

`chartink_scan_events` is the only new evidence table. The dashboard reads current active constituent memberships so curated watchlist names remain current without duplicating that information in events. It shows the latest selected-period date per source, supports 1/2/3/9-month views, and places curated-watchlist stocks first. Delivery shock and Groww Volume Shocker remain confirmation inputs for a later, explicitly agreed step.

The upload cards now show the most recently stored filename and upload time for each fixed slot. The combined dashboard can also be narrowed to a single Nifty 100, Midcap 150, Smallcap 250, or Microcap 250 table; the all-universe view remains available for comparison.
