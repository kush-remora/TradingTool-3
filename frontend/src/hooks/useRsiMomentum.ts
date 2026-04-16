import { useCallback, useEffect, useState } from "react";
import { clearCache, getJson, postJson } from "../utils/api";
import type { RsiMomentumHistoryEntry, RsiMomentumSnapshot, RsiMomentumMultiSnapshot } from "../types";

const LATEST_PATH = "/api/strategy/rsi-momentum/latest";
const REFRESH_PATH = "/api/strategy/rsi-momentum/refresh";
const HISTORY_BY_DATE_PATH = (date: string) => `/api/strategy/rsi-momentum/history/${date}`;

export function useRsiMomentum(date?: string | null) {
  const [data, setData] = useState<RsiMomentumMultiSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshingProfileId, setRefreshingProfileId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (options?: { useCache?: boolean }) => {
    setLoading(true);
    try {
      if (date) {
        // In historical mode, we need to fetch the snapshots for all profiles for this date.
        // First get the latest to know which profiles exist, then fetch history for each.
        const latest = await getJson<RsiMomentumMultiSnapshot>(LATEST_PATH);
        const profileIds = latest.profiles.map(p => p.profileId);
        
        const historicalProfiles = await Promise.all(
          profileIds.map(async (pid) => {
            try {
              const entry = await getJson<RsiMomentumHistoryEntry>(`${HISTORY_BY_DATE_PATH(date)}?profileId=${pid}`);
              return entry.snapshot;
            } catch (e) {
              console.warn(`Failed to fetch history for ${pid} on ${date}`, e);
              return null;
            }
          })
        );

        setData({
          profiles: historicalProfiles.filter((p): p is RsiMomentumSnapshot => p !== null),
          errors: [],
          partialSuccess: historicalProfiles.some(p => p !== null)
        });
      } else {
        const snapshot = await getJson<RsiMomentumMultiSnapshot>(LATEST_PATH, {
          useCache: options?.useCache ?? false,
          ttl: 15_000,
        });
        setData(snapshot);
      }
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load RSI momentum data");
    } finally {
      setLoading(false);
    }
  }, [date]);

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
