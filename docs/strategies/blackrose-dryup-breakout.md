# Wyckoff Phase C Dry-Up to Phase D Breakout
**Reference Use Case:** The "Blackrose" Pattern (May-June 2026)

## 1. Core Philosophy & The Problem with Legacy Logic
Previous attempts to find "Quiet" accumulation failed because they relied on absolute thresholds (like `Volume < 50k` or `Delivery > 65%`). This approach was deeply flawed because a high absolute delivery percentage doesn't tell the whole story, and different market caps have different baseline floats. 

To systematically identify a true Wyckoff Phase C (The Spring / Dead Silence), we must use **Relative Mathematical Benchmarks**. We are looking for the exact moment the Composite Man has fully absorbed the float, resulting in an extreme, multi-day contraction in both volume and price volatility.

## 2. Phase C: The "Dead Silent" Setup (Watchlist Criteria)
A stock qualifies as entering a legitimate Phase C accumulation phase if it passes the following relative mathematical criteria within a **Rolling 20-Day Window**:

* **1. Fundamental Quality Filter:**
  * `ROCE >= 10%`
  * *Logic:* Ensure we are tracking a fundamentally viable company, not a failing business where volume is dying due to bankruptcy risk.

* **2. Trend Gravity Guardrail:**
  * `Daily Close <= SMA(200) * 1.05`
  * *Logic:* The stock must be resting near its long-term institutional average (within 5% of the 200 SMA). This prevents us from buying stocks that are extended in Stage 2 markups or falling aggressively in Stage 4 markdowns.

* **3. Extreme Volume Dry-Up (The Lock-Up Check):**
  * `Daily Count(20, 1 where Daily Volume <= Daily Min(200, Daily Volume) * 1.1) >= 1`
  * *Logic:* In the last 20 days, there must be at least 1 day where the volume dropped to within **10% of the absolute lowest volume in the entire 200-day history**. This proves the float has completely vanished.

* **4. Volatility Squeeze (Price Contraction):**
  * `Daily Count(20, 1 where ((Daily High - Daily Low) / Daily Close) * 100 <= 2.0%) >= 5`
  * *Logic:* Volume dry-up without price contraction is an abandoned asset. We must mathematically prove the daily price bar is compressing heavily. The daily spread (High to Low) must be `<= 2.0%` for at least 5 days out of the 20-day window.

*Action:* If a stock meets these four criteria, it is added to the "Dead Silent" watchlist. We do **not** buy yet.

## 3. Phase D: The "Ignition" (Execution Criteria)
We monitor stocks on the "Dead Silent" watchlist daily. A buy alert triggers **only** when the following happens on the current trading day (`T`):

* **Volume Explosion:** Today's volume breaks violently out of the historic dry-up floor.
* **Price Breakout:** Today's price rips significantly (e.g., `> 6.0%`), cleanly breaking out of the 20-day Volatility Squeeze ceiling.

*Action:* This is the confirmation that the Composite Man has finished absorbing the float and is now triggering the Phase D Markup. This is the Buy trigger.

## Appendix: Chartink Cloud Screener Query
To offload the computational heavy lifting, run the following exact query on Chartink to generate the Phase C Watchlist. Export the resulting `Symbol` column to our local pipeline:

```text
( {cash} ( ( {cash} (  market cap >=  100 ) ) and ( {cash} (  daily count( 20, 1 where  daily volume <=  daily min ( 200 ,  daily volume ) *  1.1 ) >=  1 or  daily count( 20, 1 where  daily volume <=  daily min ( 60 ,  daily volume ) *  1.1 ) >=  1 or  daily count( 20, 1 where  daily volume <=  daily min ( 200 ,  daily volume ) *  1.05 ) >=  1 or  daily count( 20, 1 where  daily volume <=  daily min ( 60 ,  daily volume ) *  1.05 ) >=  1 ) ) and ( {cash} (  yearly return on capital employed percentage >=  10 and  yearly return on net worth percentage >=  15 and  quarterly net profit/reported profit after tax >  3 quarter ago net profit/reported profit after tax and  yearly debt equity ratio <  0.5 ) ) and ( {cash} (  daily low <=  daily sma (  daily close , 200 ) *  1.05 and  daily count( 20, 1 where  (  (  daily high -  daily low ) /  daily close ) *  100 <=  2 ) >=  5 ) ) ) )
```
