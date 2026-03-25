import { useEffect, useState } from "react";
import type { WatchlistRow } from "../types";


export function useWatchlist(tag: string = "") {
  const [rows, setRows] = useState<WatchlistRow[]>([]);
  const [loading, setLoading] = useState(true);
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
    
    // Poll every 10 seconds since L0 Caffeine Cache is 10s
    const id = setInterval(() => fetchRows(false), 10000);
    return () => clearInterval(id);
  }, [tag]);

  const refreshIndicators = async () => {
    try {
      await fetch(`${import.meta.env.VITE_API_URL || ""}/api/watchlist/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tags: tag ? [tag] : [] })
      });
      // the refresh endpoint returns 202 Accepted. Indicators will be generated in background.
    } catch (err) {
      console.error("Failed to trigger refresh", err);
    }
  };

  return { rows, loading, error, refreshIndicators };
}
