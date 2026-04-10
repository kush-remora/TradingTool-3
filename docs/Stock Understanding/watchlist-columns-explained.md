# Watchlist Columns Explained (Simple Guide)

This guide is for quick revision whenever you forget what a column means.
Think of it as a plain-language cheat sheet.

## How to use this page
- `What does it mean?` = plain explanation of the number/text.
- `Why do we need this?` = why this helps your decision.

---

## 1) SYMBOL
Example: `TMCV`, `NETWEB`

**What does it mean?**
This is the stock name/code you are tracking.

**Why do we need this?**
Without this, we cannot identify which company the row belongs to.

---

## 2) LIVE MARKET
Example: `₹443.00`

**What does it mean?**
Current market price (latest available traded price).

**Why do we need this?**
Every other number (SMA, ATR, gaps) is compared against current price.

---

## 3) SMA 50
Example: `₹452.11`

**What does it mean?**
Average closing price of the last 50 trading days.

**Why do we need this?**
Helps you see short-to-medium trend direction.
- Price above SMA 50: near-term strength.
- Price below SMA 50: near-term weakness.

---

## 4) SMA 200
Example: `₹3,016.54`

**What does it mean?**
Average closing price of the last 200 trading days.

**Why do we need this?**
Shows long-term trend health.
- Price above SMA 200: generally stronger long-term structure.
- Price below SMA 200: generally weaker long-term structure.

---

## 5) TREND STATE
Example: `Above Both`

**What does it mean?**
A quick label based on where live price is vs SMA 50 and SMA 200.
Common values:
- `Above Both`
- `Above 50 Only`
- `Above 200 Only`
- `Below Both`

**Why do we need this?**
Gives a 2-second summary of trend condition without doing mental math.

---

## 6) RANGE POSITION (60D)
Example: `47%`

**What does it mean?**
Where current price sits between last 60-day low and 60-day high.
- 0% = near 60-day low
- 100% = near 60-day high

**Why do we need this?**
Shows if stock is near bottom, middle, or top of recent range.
Useful for timing entries/exits.

---

## 7) RSI (14)
Example: `53.9`

**What does it mean?**
Momentum score from 0 to 100 over recent price movement.

**Why do we need this?**
Helps judge momentum strength.
- Higher RSI: stronger momentum
- Lower RSI: weaker momentum

Note: RSI is context-based, so keep using the raw value (as you requested) instead of fixed labels only.

---

## 8) ATR (14)
Example: `5.30% (₹23.47)`

**What does it mean?**
ATR = average daily movement over last 14 days.
- `₹23.47` = typical daily move in rupees.
- `5.30%` = same move as percentage of current price.

**Why do we need this?**
This is your risk/volatility meter.
- Helps position sizing.
- Helps setting realistic stop-loss and target distance.
- Helps compare volatility across expensive and cheap stocks.

---

## 9) DRAWDOWN %
Example: `-12.33%`

**What does it mean?**
How far current price is below its recent peak (in your current logic, from 1-year high context).

**Why do we need this?**
Shows damage from peak and risk context.
- Small negative drawdown: closer to highs.
- Large negative drawdown: still far below highs.

---

## 10) GAP TO 3M LOW %
Example: `+14.15%`

**What does it mean?**
How much current price is above the last 3-month low.

**Why do we need this?**
Helps you see how far price has already moved from recent bottom.
Lower gap means price is still close to recent lows.

---

## 11) GAP TO 3M HIGH %
Example: `12.33%`

**What does it mean?**
How far current price is below the last 3-month high.

**Why do we need this?**
Shows distance from recent resistance/high.
Smaller value means price is closer to breaking or retesting recent highs.

---

## 12) VOL VS AVG (20D)
Example: `0.91x`

**What does it mean?**
Current/last-session volume compared with 20-day average volume.
- `1.0x` = normal volume
- `>1.0x` = above normal
- `<1.0x` = below normal

**Why do we need this?**
Price moves are more trustworthy when supported by volume.
- High volume + move = stronger conviction.
- Low volume + move = weaker conviction.

---

## 13) 10D Context (Button)
Example: `10D Context`

**What does it mean?**
Opens short recent history view (last ~10 days) for quick context.

**Why do we need this?**
Lets you quickly validate whether today’s number is part of a trend or just a one-day move.

---

## Quick practical read order (simple workflow)
Use this order for fast scanning:
1. `Trend State` + `SMA 50/200` (trend)
2. `RSI` + `Range Position (60D)` (momentum and location)
3. `ATR (14)` + `Drawdown %` (risk)
4. `Gap to 3M Low/High` + `Vol vs Avg` (opportunity quality)

If you are confused on any row, open `10D Context`.
