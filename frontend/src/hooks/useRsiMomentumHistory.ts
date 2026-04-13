import { useCallback, useState } from "react";
import { getJson } from "../utils/api";
import type { RsiMomentumHistoryEntry } from "../types";

export function useRsiMomentumHistory() {
  const [data, setData] = useState<RsiMomentumHistoryEntry | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchByDate = useCallback(async (profileId: string, date: string) => {
    setLoading(true);
    setError(null);
    try {
      const entry = await getJson<RsiMomentumHistoryEntry>(
        `/api/strategy/rsi-momentum/history/${date}?profileId=${profileId}`
      );
      setData(entry);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load historical snapshot");
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  const clear = useCallback(() => {
    setData(null);
    setError(null);
  }, []);

  return {
    data,
    loading,
    error,
    fetchByDate,
    clear,
  };
}
