# Next-Day Breakout Buying Checklist

## Core principle

`Fresh breakout` means **eligible for entry review**. It does not mean automatically buying at the next day's open.

The breakout is identified using a completed daily candle. The next morning's opening price, participation, available upside, and trade risk determine whether to buy, wait, or reject the trade.

The thresholds below are conservative starting rules for manual validation and paper trading. They are not yet backtested rules.

## 1. Breakout-day quality

| Check | Strong | Wait | Reject |
|---|---:|---:|---:|
| Close position within candle | At least 80% | 60–80% | Below 60% |
| Volume vs previous 10-day average | At least 1.5× | 1.0–1.5× | Below 1.0× |
| Delivered volume vs 20-day average | At least 1.25× | Around average | Clearly below average |
| Close vs breakout line | Clearly above | Barely above | Back below |
| Close extension above breakout line | Up to 0.5 ATR | 0.5–1.0 ATR | Above 1.0 ATR: potentially late |

### Close position

```text
Close position % = (Close − Low) ÷ (High − Low) × 100
```

A close near the high shows that buyers retained control into the close.

### Volume

Compare breakout-day volume with the preceding 10 completed sessions. Expanding volume gives the resistance break more credibility.

### Delivery

Do not judge delivery percentage by itself.

```text
Delivered volume = Total traded volume × Delivery %
```

A high delivery percentage on very low total volume can still represent weak participation. Compare delivered volume with its own 20-day average.

## 2. Chart context

Before buying, answer:

- Is price above the 50-day SMA?
- Is the 50-day SMA rising?
- Is price above the 200-day SMA?
- Is the 200-day SMA flat or rising?
- Where is the next major ceiling or 52-week high?
- Is there enough upside before that obstacle?

The 200-day SMA is context, not the breakout trigger. Normally avoid a long entry below a falling 200-day SMA. If the 200-day SMA is immediately above the proposed entry, treat it as resistance.

The review console makes these words measurable:

| Context check | Console rule |
|---|---|
| 50 SMA direction | Compare today's SMA with five sessions earlier; rising is above +0.10% |
| 200 SMA direction | Compare today's SMA with twenty sessions earlier; within ±0.50% is flat |
| Long-term skip | Price below a 200 SMA falling by more than 0.50% over twenty sessions |
| Next obstacle | Nearest overhead major ceiling, prior 52-week high, or 200 SMA |
| Room before obstacle | Pass at least 2 ATR; Wait 1–1.99 ATR; Reject below 1 ATR |

These are transparent starting thresholds for manual validation. They should be revisited after paper-trading evidence, not silently changed per stock.

## 3. Next-morning execution

Do not use a fixed 5% rule. Measure the next-day opening extension using the stock's ATR.

```text
Opening extension = (Next-day open − breakout line) ÷ ATR
```

| Opening condition | Action |
|---|---|
| At or below 0.25 ATR above the breakout line | Entry may be considered if every other check passes |
| Between 0.25 and 0.50 ATR | Do not buy immediately; use a limit price or wait for a controlled pullback |
| Above 0.50 ATR | Do not chase |
| Opens below the breakout line | Do not buy at the open; the breakout is not holding |
| Opens high and immediately falls sharply | Cancel or postpone the entry |

A buy-limit order controls the maximum purchase price but does not guarantee execution. A missed trade is preferable to an uncontrolled entry.

## 4. Risk check — mandatory

Do not buy unless all of these are known before placing the order:

- Structural invalidation price: the level at which the breakout is proven wrong.
- Rupee risk per share: `planned entry − stop`.
- Maximum acceptable portfolio loss for the trade.
- Position size derived from that risk limit.
- Distance to the next major resistance.
- Potential reward of at least twice the initial risk.

```text
Position size = Maximum rupee loss allowed ÷ Risk per share
```

Reject the trade when the required stop is excessively wide or the next major resistance is too close.

## 5. External checks

Before placing the order, confirm:

- No imminent results, regulatory decision, corporate action, or major company event.
- Nifty and the stock's sector are not undergoing a significant breakdown.
- The stock is liquid enough for controlled limit-order execution.
- No abnormal news-driven gap has made the previous day's technical setup irrelevant.

## Final decision rule

Consider buying on the next session only when:

1. The completed candle is a genuine `Fresh breakout`.
2. Close position is at least 70%.
3. Volume or delivered volume shows meaningful participation.
4. The next-day opening price is not dangerously extended.
5. There is sufficient room before major resistance.
6. The invalidation price, maximum loss, and position size are defined.

If any mandatory condition fails, choose **Wait** or **Reject**. Do not force an entry simply because the scanner found a fresh breakout.

## Reference material

- [Fidelity: Volume Oscillator](https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/volume-oscillator)
- [Fidelity: Chaikin Money Flow and close location](https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/cmf)
- [Fidelity: Simple Moving Average](https://www.fidelity.com/learning-center/trading-investing/technical-analysis/technical-indicator-guide/sma)
- [NSE: Equity and security-wise delivery reports](https://www.nseindia.com/all-reports)
- [Investor.gov: Understanding order types](https://www.investor.gov/introduction-investing/general-resources/news-alerts/alerts-bulletins/investor-bulletins-14)
