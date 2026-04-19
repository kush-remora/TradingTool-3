import { useEffect, useState, useCallback } from "react";
import type { WatchlistRow } from "../types";
import { getJson, postJson } from "../utils/api";

export function useWatchlist(tag: string = "") {
  const [rows, setRows] = useState<WatchlistRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchRows = useCallback(async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      const queryParams = tag ? `?tag=${encodeURIComponent(tag)}` : "";
      const data = await getJson<WatchlistRow[]>(`/api/watchlist/rows${queryParams}`, { useCache: false });
      setRows(data || []);
      setError(null);
    } catch (err: any) {
      setError(err.message || "Failed to load watchlist data");
    } finally {
      if (showLoading) setLoading(false);
    }
  }, [tag]);

  useEffect(() => {
    fetchRows(true);
  }, [fetchRows]);

  const refreshIndicators = async (): Promise<string> => {
    setRefreshing(true);
    try {
      const payload = await postJson<{ message?: string }>(`/api/watchlist/refresh`, {
        tags: tag ? [tag] : []
      });

      await fetchRows(false);
      return payload.message ?? "All stocks refreshed successfully.";
    } catch (err: any) {
      throw err;
    } finally {
      setRefreshing(false);
    }
  };

  return { rows, loading, refreshing, error, fetchRows, refreshIndicators };
}
