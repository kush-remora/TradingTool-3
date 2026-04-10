import { useEffect, useState } from "react";
import type { TradeReadinessResponse } from "../types";
import { getJson } from "../utils/api";

export function useTradeReadiness(symbols: string[]) {
  const [data, setData] = useState<TradeReadinessResponse>({ symbols: [] });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (symbols.length === 0) {
      setData({ symbols: [] });
      setError(null);
      return;
    }

    let cancelled = false;

    const fetchReadiness = async () => {
      setLoading(true);
      try {
        const query = encodeURIComponent(symbols.join(","));
        const response = await getJson<TradeReadinessResponse>(
          `/api/trades/readiness?symbols=${query}`,
          { useCache: false },
        );
        if (!cancelled) {
          setData(response);
          setError(null);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : "Failed to load readiness data");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void fetchReadiness();
    const timer = window.setInterval(() => {
      void fetchReadiness();
    }, 60_000);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [symbols]);

  return { data, loading, error };
}
