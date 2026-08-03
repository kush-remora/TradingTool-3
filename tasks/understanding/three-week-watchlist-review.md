# Three-week watchlist review

The watchlist-level screen that compared the latest three completed weeks plus the current week was not deleted by accident. Commit `2c563cf` (2026-08-02) converted the existing `weekly-price-watchlist-scanner` route into the **Base Consolidation Low-Hit Scanner**, leaving only the single-stock `Three-Week Stock Review` route and per-stock links to it. That renamed route is why the older watchlist UI is difficult to find.

Restore the watchlist comparison as a separate, explicit route named **Three-Week Stock Review + Current Week**. Keep the newer Base Consolidation Scanner unchanged so both workflows remain available. The restored page should reuse the existing watchlist endpoint and weekly grouping utility, remain observation-first, and open the existing single-stock review for deeper inspection.

Implemented on 2026-08-03 as `ThreeWeekWatchlistReviewPage`, with the new `/console/three-week-watchlist-review` route and sidebar label. The page displays four weekly rows per stock (three preceding completed weeks plus the latest/current week), includes delivery and volume context, and opens the existing single-stock review. The focused regression test passed, the existing scanner and stock-review tests passed, and the production frontend build passed.
