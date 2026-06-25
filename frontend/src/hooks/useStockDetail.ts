import { useState, useEffect } from "react";
import { getJson } from "../utils/api";
import type { StockDetailResponse } from "../types";

export function useStockDetail(symbol: string | null, days: number = 7) {
  const [data, setData] = useState<StockDetailResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!symbol) {
      setData(null);
      setError(null);
      return;
    }
    setLoading(true);
    setError(null);
    getJson<StockDetailResponse>(`/api/stocks/by-symbol/${encodeURIComponent(symbol)}/detail?days=${days}`)
      .then(setData)
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load detail"))
      .finally(() => setLoading(false));
  }, [symbol, days]);

  return { data, loading, error };
}
