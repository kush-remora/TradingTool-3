import { useCallback, useState } from "react";
import { getJson } from "../utils/api";
import type { MultiSymbolHistoryResponse } from "../types";

export function useRsiMomentumMultiSymbolHistory() {
  const [data, setData] = useState<MultiSymbolHistoryResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (profileId: string, symbols: string[], from?: string, to?: string) => {
    if (symbols.length === 0) return;
    setLoading(true);
    setError(null);
    try {
      const symbolsCsv = symbols.join(",");
      let url = `/api/strategy/rsi-momentum/history/symbols?profileId=${profileId}&symbols=${symbolsCsv}`;
      if (from) url += `&from=${from}`;
      if (to) url += `&to=${to}`;
      
      const result = await getJson<MultiSymbolHistoryResponse>(url);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load multi-symbol history");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, load };
}
