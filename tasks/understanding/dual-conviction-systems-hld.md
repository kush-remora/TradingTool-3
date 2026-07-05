# Dual Conviction Systems — Working Understanding

This note no longer serves as the live interview capture. The interview source of truth is [dual-conviction-systems-interview-log.md](/Users/kushbhardwaj/Documents/github/TradingTool-3/tasks/understanding/dual-conviction-systems-interview-log.md), and the compiled stable HLD is now [docs/architecture/dual-conviction-systems-hld.md](/Users/kushbhardwaj/Documents/github/TradingTool-3/docs/architecture/dual-conviction-systems-hld.md).

Reason: updating the design summary after every answer can make the document internally inconsistent while major decisions are still being discovered. From this point onward:

- questions and answers are appended to the interview log
- the stable HLD is compiled from that log after major interview sweeps
- this file stays as a lightweight pointer and process note

Current stable direction:

- the product is two-system, not one-system
- the systems are logically independent even if they share factual evidence inputs
- the user wants a documentation-first flow before implementation
- manual daily ingestion and raw-evidence-first behavior remain core constraints
- the current stable HLD has been compiled from the interview log
