# Accumulation Signal Rules

## Purpose

The BHEL case study showed us what accumulation looks like in raw data. This document converts those observations into measurable rules that can be applied to any stock.

Accumulation is a process, not a moment. It typically plays out over weeks — sometimes 2 weeks, sometimes 6 weeks, depending on the stock. Our job is not to decide upfront how long it should be. Instead, we look at each day and ask: "Do the last 30 days of data show accumulation behaviour?"

That is the rolling window approach. For every single trading day, we look back 30 days and run the checks. If a stock passes multiple checks consistently over time, that is the signal.

**Why 30 days?** It is a practical starting point — roughly one month of trading. It is not sacred. Some stocks may show stronger signals in a 20-day window, others in a 45-day window. Phase 1 uses 30 days to establish a baseline. The window size itself is something we will learn from after testing across multiple stocks.

Each rule below produces a **raw number** for each stock on each day. No pass/fail. Just the data.

---

## Rule 1 — How much did the price move in the last 30 days?

**The question:** Looking back from today, how wide was the price range over the last 30 trading days?

**How to apply it:** On any given day — say April 5 — look at the closing prices of the prior 30 trading days (April 4 back to March 5). Find the highest close and the lowest close in that window. Compute the range.

```
Range % = (Highest close − Lowest close) / Lowest close × 100
```

**What the raw output looks like:** One percentage per stock per day. You compute this every day, creating a time series. When the number is falling over time, the stock is compressing.

**BHEL data — watching the compression happen:**

| Date | 30D Range % |
|---|---|
| 13 Feb | 25.2% |
| 20 Feb | 13.9% |
| 10 Mar | 11.5% |
| 11 Mar | 11.4% |
| 27 Mar | 8.0% |
| 30 Mar | 8.0% |

The range dropped from 25% to 8% over 6 weeks — cut by two thirds. This is the compression signal. The stock was covering less and less ground each week.

**What this tells us:** A compressing range after a sell-off means the selling is running out of force. The wild swings are gone. The stock is finding a level.

**What it does not tell us:** A low range alone could mean the stock is illiquid — thin trading with few participants can also produce a tight range. A stock trading 50,000 shares a day might show 6% range for that reason alone, not because of accumulation. Volume (Rule 3) is what separates the two.

**Note on the rigidity problem:** There is no universal threshold like "must be below 10%." A large-cap stock with low natural volatility might compress to 5%. A small-cap with high natural volatility might compress to 15%. Both could be valid signals. This is addressed in the final section of this document.

---

## Rule 2 — Among compressed windows, is price at a lower level than before?

**The question:** Rule 1 finds 30-day windows where the price range is tight. But a stock might have multiple tight windows over time — one where price was at ₹280–260, and another later where price was at ₹265–245. Both have the same 8% range. Which one is more likely to be accumulation?

The one where price has drifted lower. Here is why.

**The logic:** Accumulation happens when patient buyers are absorbing shares from sellers who are gradually exiting. This process takes time. As sellers exit week after week, the price drifts slightly lower — not crashing, just settling at a cheaper level. By the time you reach a compressed window at ₹245–265, the selling has done more work than the window at ₹260–280. More sellers have already exited. The stock is closer to the floor.

A compressed window at a lower price level is a stronger accumulation candidate than the same compression at a higher price level.

**How to apply it:** Once Rule 1 identifies a tight 30-day window, check where the current window sits relative to prior compressed windows:

```
Price level shift = Current window midpoint − Prior window midpoint
```

If the current window midpoint is lower than the previous compressed window's midpoint, the stock has drifted down between the two compressions. That is the signal.

**What the raw output looks like:** The midpoint of the current 30-day window (average of highest and lowest close). Track this over time — is it moving lower across successive compressed windows?

**BHEL data:**

Rule 1 found multiple compressed windows across Jan–Mar 2026. Here are the midpoints of those windows as the compression tightened:

| Date | 30D Range % | Window midpoint |
|---|---|---|
| 13 Feb | 25.2% | ~₹273 |
| 20 Feb | 13.9% | ~₹263 |
| 10 Mar | 11.5% | ~₹259 |
| 27 Mar | 8.0% | ~₹256 |
| 30 Mar | 8.0% | ~₹255 |

As the range compressed from 25% to 8%, the midpoint of each window drifted from ₹273 down to ₹255. Same compression, lower and lower price level. The stock was not just getting quieter — it was getting quieter at a progressively cheaper level.

**What this tells us:** When a stock compresses at a lower level than its previous compression, it means sellers have been gradually exiting between the two windows. The price has done work — it has come down — which means the remaining sellers are a smaller, more patient group. The closer the price gets to a genuine floor, the more likely that the next compression is real accumulation rather than a pause before more selling.

**What it does not tell us:** A lower price level does not always mean closer to a floor. A stock in structural decline will also show lower and lower compressed windows — but it will never stabilise. Volume behavior (Rule 3) is what tells you whether the compression at the lower level is stabilising or continuing to fall.

---

## Rule 3 — Are individual daily moves calm and orderly?

**The question:** Within the 30-day window Rule 1 identified, did each individual day move in small, controlled steps — or were there sudden jumps in either direction?

This is different from Rule 1. Rule 1 measures the total range over 30 days. Rule 3 looks at what happened inside each day. A stock can have a tight 30-day range and still have several violent individual days that cancelled each other out. That is not the same as calm.

**How to apply it:** For each of the last 30 trading days compute:

```
Intraday range % = (Day's high − Day's low) / Day's open × 100
Daily move % = (Day's close − Day's open) / Day's open × 100
```

Then check three things:

**Check 1 — Erratic day count:** How many of the 30 days had a daily move greater than 4%? This is the raw count. Zero is ideal. Even 2–3 erratic days in 30 is a flag.

> ⚠️ Note: Both the 4% threshold and the "2–3 days" limit are examples, not fixed rules. A high-volatility stock may naturally move 5% on quiet days. The problem with fixing these numbers is discussed in the final section of this document.

**Check 2 — Up/down day balance:** Count the up days (close above open) and down days. Are they roughly equal? Equal up and down days means neither buyers nor sellers are consistently winning — the stock is in balance. A heavily skewed count (say 22 down days out of 30) means the stock is trending down, not accumulating.

**Check 3 — Intraday range symmetry:** Compare the average H-L% on up days vs down days. If they are similar, both sides are moving with the same intensity — balanced. If down days have a much wider range than up days, sellers are being aggressive.

**What the raw output looks like:** Three numbers per stock per day — erratic day count, up/down day ratio, and the H-L% gap between up and down days.

**BHEL data — Feb 13 to Mar 30:**

| | 🟢 Up Days (22) | 🔴 Down Days (19) |
|---|---|---|
| Avg H-L % | 3.25% | 3.49% |
| Avg daily move | +1.43% | -1.67% |

- **Erratic day count:** 1 day exceeded 4% (Mar 12 at +5.49%, immediately reversed next day)
- **Up/down balance:** 22 vs 19 — nearly equal, slight edge to up days
- **H-L% gap:** 3.25% vs 3.49% — virtually identical. Sellers on down days were no more aggressive than buyers on up days

**What this tells us:** When up days and down days are nearly equal, and both sides move with similar intensity, the stock is in balance — no trend in either direction. The near-zero erratic day count confirms nobody panicked. This is the behavior of a stock where patient participants are transacting quietly without urgency.

**What it does not tell us:** Calm and balanced daily moves are a necessary condition for accumulation but not sufficient. A dull, illiquid stock also shows these numbers. Volume (Rule 4) is what tells us whether this calm reflects active absorption or simply nobody trading.

---

## Rule 4 — Is volume drying up over time?

**The question:** Over the last 30 days, are the quietest trading days getting progressively quieter — and is most of the activity coming from a few spike days rather than sustained interest?

**How to apply it:** Two measurements working together.

**Measurement A — Daily volume vs its own 5-day average:**

For each day, compare today's volume to the average volume of the prior 5 days:

```
Volume ratio = Today's volume / Average volume of prior 5 days
```

A ratio below 1.0 means today was quieter than recent days. Count how many of the last 30 days had a ratio below 1.0.

**Measurement B — Are the quiet days getting quieter?**

Look for streaks of consecutive days where each day's volume is lower than the previous day. Track the floor of each streak — the lowest volume day within it. If the floors are trending lower over time, volume is genuinely drying up.

**What the raw output looks like:**
- % of last 30 days where volume was below its 5-day average
- The lowest single-day volume in the last 30 days (the current floor)

**BHEL data:**

| Period | Days below 5D avg volume | Out of | % |
|---|---|---|---|
| Jan 1 – Feb 10 | 18 | 28 | 64% |
| Feb 10 – Mar 30 | 23 | 33 | 70% |

The descending streak floors across the full period:

| Streak start | Lowest volume in streak |
|---|---|
| Jan 12 | 5.6M |
| Jan 20 | 6.2M |
| Jan 28 | 6.3M |
| Feb 24 | 6.5M |
| Mar 13 | **4.2M ← new absolute low** |
| Mar 23 | 6.8M |

The floor was noisy in the middle — not a clean staircase down. But the first quiet spell reached 5.6M, and the last one reached 4.2M. The overall direction across 11 weeks was down. Mar 18 (4.2M) was the quietest single day in the entire period — happening near the end, not the beginning.

**What this tells us:** When the quietest days keep getting quieter, it means fewer and fewer sellers remain. Each wave of selling exhausts itself at a lower level than the one before. This is not a stock being ignored — a genuinely ignored stock has flat, random volume. This is a stock where selling is progressively running out.

The spike days (Jan 8–9 at 49M–63M, Feb 11–12 at 85M–70M) are the sell-off events. What matters is what happens after them — volume collapses and the collapse goes deeper each time.

**What it does not tell us:** Volume compression alone does not tell us who is on the buying side. It tells us sellers are exhausted. Whether patient institutional buyers are absorbing those shares — or whether it is just low activity with no buyers either — requires the price behavior rules (Rules 1 and 2) alongside it.

**Honest note on BHEL:** The volume drying-up pattern was visible across the entire Jan 12 – Mar 30 window — not just the Feb 13 onwards period. Both halves of the period showed ~64–70% of days below their 5-day average, and the descending streak floors appeared throughout. This rule does not cleanly identify Feb 13 as the start of accumulation. It confirms the entire 11-week window was a progressive seller exhaustion process. The 200D SMA rule (Rule 5) is what sharpens the window further.

---

## Rule 5 — Is price orbiting its long-term average?

**The question:** Over the last 30 days, is the stock's closing price staying close to its 200-day average price — not running far above it, not collapsing below it, just orbiting around it?

**What is the 200-day average?** Take the closing prices of the last 200 trading days and compute the average. This is the long-term reference level for the stock — where it has spent most of its recent history. Price far above it means excitement. Price far below it means distress. Price orbiting it means equilibrium.

**How to apply it:** For each day, compute:

```
Distance from 200D avg % = (Today's close − 200D average) / 200D average × 100
```

Then over the last 30 days, count how many days had this distance within ±2%, ±3%, and ±4%.

```
Proximity % = Days within band / Total days × 100
```

Report all three bands. Do not pick one threshold — report all three and let the data speak.

**What the raw output looks like:** Three proximity percentages per stock per day (one each for ±2%, ±3%, ±4% bands).

**BHEL data — three phases across Jan–Mar 2026:**

| Period | Days within ±2% of 200D avg | Out of |
|---|---|---|
| Jan 1–19 | 0 | 13 |
| Jan 20 – Feb 12 | 5 | 17 |
| **Feb 13 – Mar 27** | **23** | **29 (79%)** |
| Mar 30 | 0 | 1 |

This is the cleanest signal across all five rules. Jan 1–19 saw price 5–21% above the 200D average — too hot. Jan 20 – Feb 12 was unstable — price found the average but kept bouncing away. From Feb 13 onwards, 23 of 29 days (79%) closed within ±2%. The average was slowly rising (₹254 → ₹257) and price was sitting right on it day after day.

This is the observation that most cleanly separates the accumulation window (Feb 13 – Mar 27) from the earlier period where everything else looked similar.

**What this tells us:** When a stock stays close to its 200-day average for weeks, it means neither buyers nor sellers can move it decisively. Sellers cannot push it lower — the average acts as a floor that buyers defend. Buyers are not yet pushing it higher — they are still building their position. The stock is in balance, waiting.

**What it does not tell us:** A stock can also orbit its 200D average simply because it has no catalysts — not because of active accumulation. This rule is the strongest of the five but still needs the volume rule (Rule 4) alongside it. The combination of volume drying up AND price orbiting the 200D average is much harder to fake than either signal alone.

**Note on the rigidity problem:** The ±2% band is a starting point. A more volatile stock may naturally orbit at ±4%. Report all three bands and compare the stock's own history rather than applying a fixed threshold.
