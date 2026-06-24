## Live market stream analysis

The current `/api/market/live` path is a browser `SSE` endpoint backed by a server-side Kite `WebSocket` stream. The startup path in `Application.kt` preloads the full NSE instrument cache and then starts `KiteTickerService` with every active `index_constituents` instrument token, so the app begins live-market work as soon as the service boots, not when a page actually asks for live prices. For a single-user tool this is the main architectural mismatch: Phase D / GPM pages may only need tens of symbols, but the backend is prepared to stream the whole active universe immediately.

The endpoint and subscription lifecycle also leak work. `LiveMarketResource` adds instruments when a client connects, but never removes them when the client disconnects, and `KiteTickerService` keeps those tokens forever. Each SSE request also creates a dedicated daemon thread that wakes every second, re-resolves symbols to tokens, builds update payloads, and blocks on `send(...).get()`. That means reconnects and symbol-set changes create fresh long-lived threads, while the upstream Kite subscription list only grows. The current frontend manager reduces duplicate browser connections, but it still depends on a backend model that is doing too much by default and never shrinks.

### Current findings

- Startup cost is dominated by eager ticker subscription, not just page-level usage.
- The backend subscribes by token but has no server-side reference counting or disconnect cleanup.
- The SSE implementation is polling-based instead of event-driven, so it keeps doing work even when no ticks changed.
- The API contract is partially fake today: `buyerDominancePct` is accepted but ignored, and response fields like `volumeHeat` stay `null`.
- Full-tick mode is used for every subscribed instrument even though the current widgets mostly need LTP, OHLC, and volume.

### Validation state

This started as a static architecture/code-path review because there was no active process on port `8080` during the investigation. The implementation follow-up is now in place:

- startup-wide ticker subscription was removed
- ticker subscriptions are now lazy and reference-counted
- `/api/market/live` now streams off tick events instead of per-client polling threads
- frontend live requests are gated by NSE market hours
- frontend unload/unmount now closes the live SSE connection

Focused validation after the patch:

- `mvn -pl core,event-service,resources,service -am test -Dtest=NseMarketClockTest -Dsurefire.failIfNoSpecifiedTests=false` passed
- `npm --prefix frontend test -- --run src/utils/marketHours.test.ts` passed
- `npm --prefix frontend run build` passed

One unrelated existing frontend test still timed out during a broader `PhaseDScannerPage.test.tsx` run; the live-market code paths compiled and the targeted checks above passed.
