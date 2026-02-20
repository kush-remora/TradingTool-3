import { useCallback, useEffect, useState } from "react";
import { deleteJson, getJson, patchJson, postJson } from "../utils/api";
import type { Watchlist } from "../types";

export function useWatchlists() {
  const [watchlists, setWatchlists] = useState<Watchlist[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getJson<Watchlist[]>("/api/watchlist/lists?limit=200");
      setWatchlists(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to fetch watchlists");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchAll();
  }, [fetchAll]);

  const createWatchlist = async (name: string, description?: string) => {
    const created = await postJson<Watchlist>("/api/watchlist/lists", {
      name,
      description: description ?? null,
    });
    setWatchlists((prev) => [created, ...prev]);
    return created;
  };

  const renameWatchlist = async (id: number, name: string) => {
    const updated = await patchJson<Watchlist>(`/api/watchlist/lists/${id}`, {
      name,
    });
    setWatchlists((prev) => prev.map((w) => (w.id === id ? updated : w)));
    return updated;
  };

  const removeWatchlist = async (id: number) => {
    await deleteJson(`/api/watchlist/lists/${id}`);
    setWatchlists((prev) => prev.filter((w) => w.id !== id));
  };

  return {
    watchlists,
    loading,
    error,
    refetch: fetchAll,
    createWatchlist,
    renameWatchlist,
    removeWatchlist,
  };
}
