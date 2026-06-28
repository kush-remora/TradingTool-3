# The Hybrid Wyckoff Validation Pipeline

## 1. Architectural Overview
This system uses a hybrid approach to validate Wyckoff Accumulation before taking trades on Momentum setups (like 52-week highs). 
It leverages the speed and experimentation flexibility of Chartink for broad mathematical filtering, and the programmatic power of a Kotlin backend for advanced structural analysis and human-bias removal.

The pipeline is split into two physical environments:
1. **The Discovery Engine (Chartink):** A web-based screener running off-the-shelf math rules to narrow 5,000 stocks down to a handful.
2. **The Validation Engine (Kotlin Backend):** A custom backend script that ingests the Chartink CSV, fetches raw market data via Kite, and runs advanced logic that off-the-shelf screeners cannot handle (like Shape Classification).

## 2. Step 1: The Chartink Stratification (The Universe)
Because Chartink cannot dynamically change math rules based on the size of a company, we cannot run a single scan for the entire market. Instead, the universe is restricted to the **Nifty 500**, broken into three separate Chartink screeners:

1. **The Large Cap Screener (Nifty 100):** Uses the tightest thresholds (e.g., `< 3.5%` volatility, `10%` DMA band).
2. **The Mid Cap Screener (Nifty Midcap 150):** Uses moderate thresholds (e.g., `< 4.5%` volatility, `15%` DMA band).
3. **The Small Cap Screener (Nifty Smallcap 250):** Uses the loosest thresholds to forgive higher baseline volatility (e.g., `< 6%` volatility, `20%` DMA band).

**The Output:** You run all three screeners at the end of the day, download the three resulting CSV files, and combine them. This combined list represents every stock in the Nifty 500 that currently exhibits the mathematical footprint of Wyckoff Accumulation.
