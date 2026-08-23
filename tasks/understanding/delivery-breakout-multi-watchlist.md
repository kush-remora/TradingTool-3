# Delivery Breakout Validation — Multi-watchlist Run

The Delivery Breakout Validation console must support selecting one or more saved watchlists and running the same 10-session volume/delivery event scan across all selected watchlists. A single selected watchlist keeps the existing endpoint and response behavior. Multiple selections use a dedicated endpoint, run with bounded concurrency, aggregate event totals in the summary, and retain the source watchlist on every table and CSV row so duplicate symbols remain auditable.

The chart/review links and current event filters remain unchanged. If one watchlist fails, successful watchlists still render and the UI reports the failed watchlist and error message. The feature does not change detection thresholds, trading-date selection, ETF exclusion, or event classification.

The table's compact Price Context cell should show live LTP, event-day close, LTP versus event close, event-day low-to-high range, previous-close-to-event-close change, and event-day close position from low to high. Positive/negative changes use green/red signs, while the close position uses a restrained red/amber/green cue. The Price Context column is sortable by previous-close-to-event-close percentage.
