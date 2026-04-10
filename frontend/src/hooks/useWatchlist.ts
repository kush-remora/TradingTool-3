import { useEffect, useState } from "react";
import type { WatchlistRow } from "../types";


export function useWatchlist(tag: string = "") {
  const [rows, setRows] = useState<WatchlistRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchRows = async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      const queryParams = tag ? `?tag=${encodeURIComponent(tag)}` : "";
      const response = await fetch(`${import.meta.env.VITE_API_URL || ""}/api/watchlist/rows${queryParams}`);
      if (!response.ok) {
        throw new Error(`Failed to fetch watchlist: ${response.statusText}`);
      }
      const data = await response.json();
      setRows(data);
      setError(null);
    } catch (err: any) {
      setError(err.message || "Failed to load watchlist data");
    } finally {
      if (showLoading) setLoading(false);
    }
  };

  useEffect(() => {
    fetchRows(true);
    return undefined;
  }, [tag]);

  const refreshIndicators = async (): Promise<string> => {
    setRefreshing(true);
    try {
      const response = await fetch(`${import.meta.env.VITE_API_URL || ""}/api/watchlist/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tags: tag ? [tag] : [] })
      });

      if (!response.ok) {
        throw new Error(`Refresh failed: ${response.statusText}`);
      }

      const payload = await response.json().catch(() => ({} as { message?: string }));
      await fetchRows(false);
      return payload.message ?? "All stocks refreshed successfully.";
    } finally {
      setRefreshing(false);
    }
  };

  return { rows, loading, refreshing, error, refreshIndicators };
}
