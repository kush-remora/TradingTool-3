import { useCallback, useState } from "react";
import { getJson } from "../utils/api";
import type { RsiMomentumHistoryEntry } from "../types";

export function useRsiMomentumBaseHistory() {
  const [data, setData] = useState<RsiMomentumHistoryEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (profileId: string, fromDate: string, toDate: string) => {
    setLoading(true);
    setError(null);
    try {
      const query = `?profileId=${encodeURIComponent(profileId)}&from=${encodeURIComponent(fromDate)}&to=${encodeURIComponent(toDate)}`;
      const entries = await getJson<RsiMomentumHistoryEntry[]>(`/api/strategy/rsi-momentum/history${query}`, {
        useCache: false,
      });
      setData(entries);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load momentum history.");
      setData([]);
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, load };
}
