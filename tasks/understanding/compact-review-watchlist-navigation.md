# Compact review watchlist navigation

The compact review is a daily reading surface, so the user should be able to move through a selected watchlist without leaving the page. The existing stock search remains independent: a stock can be opened directly even when it is not a member of the selected watchlist.

The implementation adds a small watchlist selector and previous/next controls below the stock identity. Watchlist membership is loaded through a lightweight stock endpoint, the current position is shown as `n/total`, and the `symbol` plus `watchlist` query parameters keep the review state shareable and refresh-safe. When an independent search is outside the selected list, the position becomes `—`; navigation can re-enter the list from its first or last member.
