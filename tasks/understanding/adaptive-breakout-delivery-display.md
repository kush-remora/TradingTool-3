# Adaptive breakout delivery display

The adaptive-breakout scanner already had delivery data in the stock-detail API, but its 20-session evidence mapper hard-coded `deliveryPercentage: null`. The historical breakout-review API also returned BHEL delivery correctly, but the review card only surfaced the percentage and gave no delivered-quantity snapshot, making the evidence look incomplete.

The fix keeps the existing delivery source of truth. When the 20D modal opens, it loads the selected stock's detail payload and joins `delivery_days` by ISO date onto the scanner candles. The breakout-review page displays both delivery percentage and delivered quantity, with a stock-detail fallback if the structure response has either value unavailable. Missing data remains visibly unavailable rather than guessed.
