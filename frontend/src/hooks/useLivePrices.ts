import { useEffect, useState } from "react";
import type { TickSnapshot } from "../types";
import { apiBaseUrl } from "../utils/api";

/**
 * Opens a single SSE connection to /api/watchlist/stream and maintains a map
 * of the latest tick per instrument token.
 *
 * Usage:
 *   const { getTick } = useLivePrices();
 *   const tick = getTick(stock.instrument_token);
 *   if (tick) console.log(tick.ltp, tick.changePercent);
 *
 * The EventSource auto-reconnects on network errors — no manual retry needed.
 */
export function useLivePrices() {
  const [ticks, setTicks] = useState<Map<number, TickSnapshot>>(new Map());

  useEffect(() => {
    const es = new EventSource(`${apiBaseUrl}/api/watchlist/stream`);

    es.onmessage = (event: MessageEvent) => {
      try {
        const tick = JSON.parse(event.data as string) as TickSnapshot;
        setTicks((prev) => {
          const next = new Map(prev);
          next.set(tick.instrumentToken, tick);
          return next;
        });
      } catch {
        // Malformed event — ignore silently.
      }
    };

    // EventSource reconnects automatically on error — no action needed here.
    es.onerror = () => {};

    return () => {
      es.close();
    };
  }, []);

  const getTick = (instrumentToken: number): TickSnapshot | undefined =>
    ticks.get(instrumentToken);

  return { getTick };
}
