import { useCallback, useEffect, useState } from "react";
import { clearCache, getJson, postJson } from "../utils/api";
import type { S4MultiSnapshot } from "../types";

const LATEST_PATH = "/api/strategy/s4/latest";
const REFRESH_PATH = "/api/strategy/s4/refresh";

export function useS4VolumeSpike() {
  const [data, setData] = useState<S4MultiSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (options?: { useCache?: boolean }) => {
    setLoading(true);
    try {
      const snapshot = await getJson<S4MultiSnapshot>(LATEST_PATH, {
        useCache: options?.useCache ?? false,
        ttl: 15_000,
      });
      setData(snapshot);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load S4 snapshot");
    } finally {
      setLoading(false);
    }
  }, []);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await postJson<S4MultiSnapshot>(REFRESH_PATH, {});
      clearCache(LATEST_PATH);
      await load({ useCache: false });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to refresh S4 snapshot");
    } finally {
      setRefreshing(false);
    }
  }, [load]);

  useEffect(() => {
    void load({ useCache: false });
  }, [load]);

  return {
    data,
    loading,
    refreshing,
    error,
    refetch: load,
    refresh,
  };
}
