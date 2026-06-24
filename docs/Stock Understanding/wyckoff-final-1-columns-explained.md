# Wyckoff Final CSV Columns Explained

This guide explains the columns from `/Users/kushbhardwaj/Downloads/wycoff final 1 (2).csv` in plain language.

A few original headers in the CSV are messy because the export contains formula-style names with commas inside them. This document uses cleaned names so the file is easier to understand and map into code later.

## Short-name mapping

| Original CSV Column | Suggested Short Name |
|---|---|
| `Sr.` | `sr_no` |
| `Stock Name` | `stock_name` |
| `Symbol` | `symbol` |
| `marketcapname` | `market_cap_bucket` |
| `close` | `close_price` |
| `%_change` | `pct_change` |
| `volume` | `volume` |
| `sector` | `sector` |
| `industry` | `industry` |
| `volume dry on 200 days min` | `vol_dry_200d_min_count` |
| `volume dry on 60 days min` | `vol_dry_60d_min_count` |
| `volume dry on 200 days min * 1.05` | `vol_dry_200d_min_105_count` |
| `volume dry on 60days min * 1.05` | `vol_dry_60d_min_105_count` |
| `Yearly Return on capital employed percentage` | `roce_pct` |
| `Yearly Return on net worth percentage` | `ronw_pct` |
| `3 quarter ago Net profit/reported profit after tax` | `net_profit_after_tax` |
| `Yearly Debt equity ratio` | `debt_equity_ratio` |
| `count daily atr < 2 %` | `atr_lt_2pct_count` |
| `Quarterly Indian promoter and group percentage` | `indian_promoter_pct` |
| `Quarterly Total foreign promoter and group percentage` | `foreign_promoter_pct` |
| `Quarterly Gross sales` | `quarterly_gross_sales` |
| `1 day ago Max( 252 , Daily High )` | `high_52w` |
| `1 day ago Min( 20 , Daily High )` | `low_52w` |
| `( Daily Close - Daily Max( 200 , Daily High ) / Daily Max( 200 , Daily High ) )` | `dist_200d_high_pct` |
| `brackets2` | `dist_200d_low_pct` |
| `Add Column` | `unused_empty_column` |

## Column-by-column explanation

### 1) `sr_no`
Original: `Sr.`

**What does it mean?**  
Simple serial number for the row.

**Why is it useful?**  
Only for ordering/reference. Not a market signal.

---

### 2) `stock_name`
Original: `Stock Name`

**What does it mean?**  
Full company name.

**Why is it useful?**  
Makes the watchlist human-readable.

---

### 3) `symbol`
Original: `Symbol`

**What does it mean?**  
Exchange trading symbol.

**Why is it useful?**  
Primary identity used for matching market data, tokens, scans, and trades.

---

### 4) `market_cap_bucket`
Original: `marketcapname`

**What does it mean?**  
Market cap classification such as `largecap`, `midcap`, or `smallcap`.

**Why is it useful?**  
Helps apply different expectations and thresholds by cap bucket.

---

### 5) `close_price`
Original: `close`

**What does it mean?**  
Latest daily closing price.

**Why is it useful?**  
Base price used in distance and structure calculations.

---

### 6) `pct_change`
Original: `%_change`

**What does it mean?**  
Daily percentage change in price.

**Why is it useful?**  
Shows whether the stock was up or down on the latest day.

---

### 7) `volume`
Original: `volume`

**What does it mean?**  
Latest traded volume.

**Why is it useful?**  
Important for judging whether dry-up conditions exist and whether a move has participation.

---

### 8) `sector`
Original: `sector`

**What does it mean?**  
Broad business sector.

**Why is it useful?**  
Helps compare names within the same broad market group.

---

### 9) `industry`
Original: `industry`

**What does it mean?**  
More specific business classification inside the sector.

**Why is it useful?**  
Useful when comparing very similar businesses rather than broad sectors only.

---

### 10) `vol_dry_200d_min_count`
Original: `volume dry on 200 days min`

**What does it mean?**  
Flag/count related to whether current volume is near the 200-day minimum volume dry-up condition.

**Why is it useful?**  
This is part of the quiet-activity / supply-exhaustion screening logic.

---

### 11) `vol_dry_60d_min_count`
Original: `volume dry on 60 days min`

**What does it mean?**  
Flag/count related to whether current volume is near the 60-day minimum volume dry-up condition.

**Why is it useful?**  
Gives a shorter-window dry-up signal than the 200-day version.

---

### 12) `vol_dry_200d_min_105_count`
Original: `volume dry on 200 days min * 1.05`

**What does it mean?**  
Dry-up check using a slightly relaxed threshold: within `105%` of the 200-day minimum volume.

**Why is it useful?**  
Catches near-dry-up conditions without requiring an exact extreme low.

---

### 13) `vol_dry_60d_min_105_count`
Original: `volume dry on 60days min * 1.05`

**What does it mean?**  
Dry-up check using a slightly relaxed threshold: within `105%` of the 60-day minimum volume.

**Why is it useful?**  
Same idea as above, but on the shorter 60-day lookback.

---

### 14) `roce_pct`
Original: `Yearly Return on capital employed percentage`

**What does it mean?**  
Annual return on capital employed.

**Why is it useful?**  
Acts as a quality filter. Higher ROCE usually suggests better capital efficiency.

---

### 15) `ronw_pct`
Original: `Yearly Return on net worth percentage`

**What does it mean?**  
Annual return on net worth.

**Why is it useful?**  
Another business quality check, especially useful when comparing management efficiency.

---

### 16) `net_profit_after_tax`
Original: `3 quarter ago Net profit/reported profit after tax`

**What does it mean?**  
Quarterly net profit, also described as reported profit after tax.

**Why is it useful?**  
Adds current profitability context when reviewing candidate quality.

---

### 17) `debt_equity_ratio`
Original: `Yearly Debt equity ratio`

**What does it mean?**  
Debt-to-equity ratio.

**Why is it useful?**  
Helps avoid weak balance-sheet situations when screening otherwise interesting structures.

---

### 18) `atr_lt_2pct_count`
Original: `count daily atr < 2 %`

**What does it mean?**  
Count of days where daily ATR was below `2%`.

**Why is it useful?**  
Lower ATR often supports the idea of contraction, quietness, or coiling behavior.

---

### 19) `indian_promoter_pct`
Original: `Quarterly Indian promoter and group percentage`

**What does it mean?**  
Shareholding percentage held by Indian promoters and promoter group.

**Why is it useful?**  
Useful as an ownership-quality context field.

---

### 20) `foreign_promoter_pct`
Original: `Quarterly Total foreign promoter and group percentage`

**What does it mean?**  
Shareholding percentage held by foreign promoter/group participants.

**Why is it useful?**  
Adds ownership context and helps interpret control structure.

---

### 21) `quarterly_gross_sales`
Original: `Quarterly Gross sales`

**What does it mean?**  
Gross sales for the latest quarter.

**Why is it useful?**  
Basic business-size / revenue context.

---

### 22) `high_52w`
Original: `1 day ago Max( 252 , Daily High )`

**What does it mean?**  
Previous day rolling `52-week high` based on the highest daily high in the last `252` trading days.

**Why is it useful?**  
Shows how far price is from long-term overhead resistance.

---

### 23) `low_52w`
Original: `1 day ago Min( 20 , Daily High )`

**What does it mean?**  
You clarified this should be treated as `52-week low`, even though the export header is wrong.

**Why is it useful?**  
Gives the lower long-term structure reference point.

---

### 24) `dist_200d_high_pct`
Original: `( Daily Close - Daily Max( 200 , Daily High ) / Daily Max( 200 , Daily High ) )`

**What does it mean?**  
Percentage distance of current close from the `200-day highest high`.

**Why is it useful?**  
Negative values show how far price still is below major overhead supply / long-term resistance.

---

### 25) `dist_200d_low_pct`
Original: `brackets2`

**Formula you provided:**  
`((Daily Close - Daily Min(20, Daily Low)) / Daily Min(20, Daily Low)) * 100`

**What does it mean?**  
Percentage distance of current close above the `20-day lowest low`.

**Why is it useful?**  
Shows how far price has lifted from recent support or short-term floor.

---

### 26) `unused_empty_column`
Original: `Add Column`

**What does it mean?**  
This column is empty for every row in the current CSV.

**Why is it useful?**  
Right now it is not useful. It looks like a leftover export artifact and can be ignored unless you later assign meaning to it.

## Practical quick read order

If you want to scan this CSV fast, this order is useful:

1. `symbol`, `market_cap_bucket`, `sector`
2. `close_price`, `pct_change`, `volume`
3. `vol_dry_200d_min_count`, `vol_dry_60d_min_count`, `vol_dry_200d_min_105_count`, `vol_dry_60d_min_105_count`
4. `atr_lt_2pct_count`, `dist_200d_high_pct`, `dist_200d_low_pct`
5. `roce_pct`, `ronw_pct`, `debt_equity_ratio`, promoter fields

That order gives you:
- identity
- current behavior
- dry-up evidence
- structure context
- quality/safety context
