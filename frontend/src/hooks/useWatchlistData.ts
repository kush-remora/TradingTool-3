import { useCallback, useEffect, useState } from "react";
import { getJson, postJson, deleteJson } from "../utils/api";
import type {
  Stock,
  StockTag,
  Tag,
  Watchlist,
  WatchlistStock,
  WatchlistTag,
} from "../types";

export interface WatchlistDataState {
  watchlists: Watchlist[];
  stocks: Stock[];
  allTags: Tag[];
  watchlistStocks: WatchlistStock[];  // all junction records across all watchlists
  stockTags: StockTag[];              // all stock-tag junction records
  watchlistTags: WatchlistTag[];      // all watchlist-tag junction records
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
  addStockToWatchlist: (watchlistId: number, stockId: number) => Promise<void>;
  removeStockFromWatchlist: (watchlistId: number, stockId: number) => Promise<void>;
  createWatchlist: (name: string, description?: string) => Promise<Watchlist>;
  updateWatchlist: (id: number, fields: { name?: string; description?: string }) => Promise<void>;
  deleteWatchlist: (id: number) => Promise<void>;
  updateStock: (id: number, fields: { description?: string; priority?: number }) => Promise<void>;
  addTagToStock: (stockId: number, tagName: string) => Promise<void>;
  removeTagFromStock: (stockId: number, tagId: number) => Promise<void>;
  addTagToWatchlist: (watchlistId: number, tagName: string) => Promise<void>;
  removeTagFromWatchlist: (watchlistId: number, tagId: number) => Promise<void>;
}

export function useWatchlistData(): WatchlistDataState {
  const [watchlists, setWatchlists] = useState<Watchlist[]>([]);
  const [stocks, setStocks] = useState<Stock[]>([]);
  const [allTags, setAllTags] = useState<Tag[]>([]);
  const [watchlistStocks, setWatchlistStocks] = useState<WatchlistStock[]>([]);
  const [stockTags, setStockTags] = useState<StockTag[]>([]);
  const [watchlistTags, setWatchlistTags] = useState<WatchlistTag[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // Load all data in parallel — personal tool, small dataset
      const [wlData, stockData, tagData, stockTagData, wlTagData] = await Promise.all([
        getJson<Watchlist[]>("/api/watchlist/lists?limit=200"),
        getJson<Stock[]>("/api/watchlist/stocks?limit=500"),
        getJson<Tag[]>("/api/watchlist/tags?limit=500"),
        getJson<StockTag[]>("/api/watchlist/stock-tags/all"),
        getJson<WatchlistTag[]>("/api/watchlist/watchlist-tags/all"),
      ]);

      // Fetch junction records for all watchlists in parallel
      const junctionArrays = await Promise.all(
        wlData.map((wl) =>
          getJson<WatchlistStock[]>(`/api/watchlist/lists/${wl.id}/items`),
        ),
      );
      const allJunctions = junctionArrays.flat();

      setWatchlists(wlData);
      setStocks(stockData);
      setAllTags(tagData);
      setWatchlistStocks(allJunctions);
      setStockTags(stockTagData);
      setWatchlistTags(wlTagData);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load watchlist data");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchAll();
  }, [fetchAll]);

  const addStockToWatchlist = async (watchlistId: number, stockId: number) => {
    const junction = await postJson<WatchlistStock>("/api/watchlist/items", {
      watchlist_id: watchlistId,
      stock_id: stockId,
    });
    setWatchlistStocks((prev) => [...prev, junction]);
  };

  const removeStockFromWatchlist = async (watchlistId: number, stockId: number) => {
    await deleteJson(`/api/watchlist/items/${watchlistId}/${stockId}`);
    setWatchlistStocks((prev) =>
      prev.filter((ws) => !(ws.watchlist_id === watchlistId && ws.stock_id === stockId)),
    );
  };

  const createWatchlist = async (name: string, description?: string): Promise<Watchlist> => {
    const wl = await postJson<Watchlist>("/api/watchlist/lists", {
      name,
      description: description ?? null,
    });
    setWatchlists((prev) => [...prev, wl]);
    return wl;
  };

  const updateWatchlist = async (id: number, fields: { name?: string; description?: string }) => {
    const updated = await import("../utils/api").then((m) =>
      m.patchJson<Watchlist>(`/api/watchlist/lists/${id}`, fields),
    );
    setWatchlists((prev) => prev.map((w) => (w.id === id ? updated : w)));
  };

  const deleteWatchlist = async (id: number) => {
    await deleteJson(`/api/watchlist/lists/${id}`);
    setWatchlists((prev) => prev.filter((w) => w.id !== id));
    setWatchlistStocks((prev) => prev.filter((ws) => ws.watchlist_id !== id));
    setWatchlistTags((prev) => prev.filter((wt) => wt.watchlist_id !== id));
  };

  const updateStock = async (id: number, fields: { description?: string; priority?: number }) => {
    const updated = await import("../utils/api").then((m) =>
      m.patchJson<Stock>(`/api/watchlist/stocks/${id}`, fields),
    );
    setStocks((prev) => prev.map((s) => (s.id === id ? updated : s)));
  };

  const addTagToStock = async (stockId: number, tagName: string) => {
    const tag = await postJson<Tag>(`/api/watchlist/stocks/${stockId}/tags`, { tagName });
    // Ensure tag is in allTags
    setAllTags((prev) => (prev.some((t) => t.id === tag.id) ? prev : [...prev, tag]));
    setStockTags((prev) =>
      prev.some((st) => st.stock_id === stockId && st.tag_id === tag.id)
        ? prev
        : [...prev, { id: Date.now(), stock_id: stockId, tag_id: tag.id, created_at: new Date().toISOString() }],
    );
  };

  const removeTagFromStock = async (stockId: number, tagId: number) => {
    await deleteJson(`/api/watchlist/stocks/${stockId}/tags/${tagId}`);
    setStockTags((prev) => prev.filter((st) => !(st.stock_id === stockId && st.tag_id === tagId)));
  };

  const addTagToWatchlist = async (watchlistId: number, tagName: string) => {
    const tag = await postJson<Tag>(`/api/watchlist/lists/${watchlistId}/tags`, { tagName });
    setAllTags((prev) => (prev.some((t) => t.id === tag.id) ? prev : [...prev, tag]));
    setWatchlistTags((prev) =>
      prev.some((wt) => wt.watchlist_id === watchlistId && wt.tag_id === tag.id)
        ? prev
        : [...prev, { id: Date.now(), watchlist_id: watchlistId, tag_id: tag.id, created_at: new Date().toISOString() }],
    );
  };

  const removeTagFromWatchlist = async (watchlistId: number, tagId: number) => {
    await deleteJson(`/api/watchlist/lists/${watchlistId}/tags/${tagId}`);
    setWatchlistTags((prev) =>
      prev.filter((wt) => !(wt.watchlist_id === watchlistId && wt.tag_id === tagId)),
    );
  };

  return {
    watchlists,
    stocks,
    allTags,
    watchlistStocks,
    stockTags,
    watchlistTags,
    loading,
    error,
    refetch: fetchAll,
    addStockToWatchlist,
    removeStockFromWatchlist,
    createWatchlist,
    updateWatchlist,
    deleteWatchlist,
    updateStock,
    addTagToStock,
    removeTagFromStock,
    addTagToWatchlist,
    removeTagFromWatchlist,
  };
}
