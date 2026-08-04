# Delivery Breakout Watchlist 10-Day Validation

The Delivery Breakout screen is being changed from a latest-day, full-delivery-table filter into a selected-watchlist event scanner. It evaluates the latest ten available trading sessions, comparing each day independently with the preceding ten sessions. A day is shown when volume, delivery quantity, or both reach the configured 2x shock threshold.

The UI uses existing saved watchlist/index memberships, defaults to the latest available delivery date, and supports an end-date override. The result is event-focused: one row per stock-day with `BOTH`, `DELIVERY_ONLY`, or `VOLUME_ONLY`, while stocks without events are represented in summary counts. Delivery quantity is the detection metric; delivery percentage remains contextual.
