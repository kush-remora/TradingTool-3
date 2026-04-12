import { useCallback, useEffect, useState } from "react";
import { clearCache, getJson, postJson } from "../utils/api";
import type { RsiMomentumMultiSnapshot } from "../types";

const LATEST_PATH = "/api/strategy/rsi-momentum/latest";
const REFRESH_PATH = "/api/strategy/rsi-momentum/refresh";

export function useRsiMomentum() {
  const [data, setData] = useState<RsiMomentumMultiSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshingProfileId, setRefreshingProfileId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (options?: { useCache?: boolean }) => {
    setLoading(true);
    try {
      const snapshot = await getJson<RsiMomentumMultiSnapshot>(LATEST_PATH, {
        useCache: options?.useCache ?? false,
        ttl: 15_000,
      });
      setData(snapshot);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load RSI momentum snapshot");
    } finally {
      setLoading(false);
    }
  }, []);

  const refresh = useCallback(async (profileId?: string) => {
    setRefreshing(true);
    setRefreshingProfileId(profileId ?? null);
    try {
      await postJson<RsiMomentumMultiSnapshot>(REFRESH_PATH, {});
      clearCache(LATEST_PATH);
      await load({ useCache: false });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to refresh RSI momentum snapshot");
    } finally {
      setRefreshing(false);
      setRefreshingProfileId(null);
    }
  }, [load]);

  useEffect(() => {
    void load({ useCache: false });
  }, [load]);

  return {
    data,
    loading,
    refreshing,
    refreshingProfileId,
    error,
    refetch: load,
    refresh,
  };
}
