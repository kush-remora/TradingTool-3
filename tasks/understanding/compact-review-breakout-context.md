# Compact review breakout context

The breakout date alone is incomplete for daily review. The compact header should show the threshold that was crossed and how far the current price is from that level, so a historical breakout is not mistaken for a current signal.

The stock-detail response keeps the existing breakout dates and adds one level per horizon. The compact secondary row renders each as `20D · date · level · distance`, with distance signed by current price relative to the crossed prior rolling high. No “buy”, “active”, or “failed” interpretation is added in this step.
