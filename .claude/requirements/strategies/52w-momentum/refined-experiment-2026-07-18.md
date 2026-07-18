# Refined Experiment — 2026-07-18

| Universe | Fair value | Calm body | Persistence |
| --- | --- | --- | --- |
| Nifty 100 | ±5% of 200 DMA | <= 2% | >= 36 / 60 |
| Nifty Midcap 150 | ±7% of 200 DMA | <= 3% | >= 36 / 60 |
| Nifty Smallcap 250 | ±10% of 200 DMA | <= 4% | >= 36 / 60 |
| Nifty Microcap 250 | ±12% of 200 DMA | <= 5% | >= 36 / 60 |

## Logic

1. ```chartink
   daily count( 60, 1 where abs( daily open - daily close ) / daily open * 100 <= 2 ) >= 36
   ```

2. ```chartink
   daily countstreak( 20, 1 where daily volume <= 1 day ago volume ) >= 3
   ```

3. ```chartink
   daily countstreak( 3, 1 where abs( daily close - daily sma( daily close, 200 ) ) / daily sma( daily close, 200 ) * 100 <= 5 ) >= 3
   ```

## Combined Template

```chartink
( {33619} ( daily count( 60, 1 where abs( daily open - daily close ) / daily open * 100 <= 2 ) >= 36 and daily countstreak( 20, 1 where daily volume <= 1 day ago volume ) >= 3 and daily countstreak( 3, 1 where abs( daily close - daily sma( daily close, 200 ) ) / daily sma( daily close, 200 ) * 100 <= 5 ) >= 3 ) )
```
