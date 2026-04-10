import { useCallback, useEffect, useMemo, useState } from "react";
import { getJson, patchJson, postJson, deleteJson } from "../utils/api";
import type { Stock, StockTag } from "../types";

export interface CreateStockInput {
  symbol: string;
  instrument_token: number;
  company_name: string;
  exchange: string;
  notes?: string;
  priority?: number;
  tags?: StockTag[];
}

export interface UpdateStockInput {
  notes?: string;
  priority?: number;
  tags?: StockTag[];
}

interface UseStocksResult {
  stocks: Stock[];
  allTags: StockTag[];           // unique tags derived from all stocks — for legacy/scanners
  configTags: StockTag[];        // pre-defined tags from watchlist_config.json
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
  filterByTag: (tagName: string | null) => Stock[];
  createStock: (payload: CreateStockInput) => Promise<Stock>;
  updateStock: (stockId: number, payload: UpdateStockInput) => Promise<Stock>;
  deleteStock: (stockId: number) => Promise<void>;
}

// Module-level cache: shared across all component instances
let cachedStocks: Stock[] | null = null;
let cachedConfigTags: StockTag[] | null = null;
let fetchPromise: Promise<Stock[]> | null = null;
let configTagsFetchPromise: Promise<StockTag[]> | null = null;
let stocksChangeListeners: ((stocks: Stock[]) => void)[] = [];
let configTagsChangeListeners: ((tags: StockTag[]) => void)[] = [];

function notifyStocksChange(stocks: Stock[]) {
  cachedStocks = stocks;
  stocksChangeListeners.forEach((listener) => listener(stocks));
}

function notifyConfigTagsChange(tags: StockTag[]) {
  cachedConfigTags = tags;
  configTagsChangeListeners.forEach((listener) => listener(tags));
}

export function useStocks(): UseStocksResult {
  const [stocks, setStocks] = useState<Stock[]>(cachedStocks ?? []);
  const [configTags, setConfigTags] = useState<StockTag[]>(cachedConfigTags ?? []);
  const [loading, setLoading] = useState(cachedStocks === null || cachedConfigTags === null);
  const [error, setError] = useState<string | null>(null);

  const fetchAll = useCallback(async () => {
    // 1. Fetch Stocks
    if (cachedStocks === null && fetchPromise === null) {
      setLoading(true);
      setError(null);
      fetchPromise = getJson<Stock[]>("/api/stocks");
      try {
        const data = await fetchPromise;
        notifyStocksChange(data);
        setStocks(data);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to fetch stocks");
      } finally {
        fetchPromise = null;
      }
    }

    // 2. Fetch Config Tags
    if (cachedConfigTags === null && configTagsFetchPromise === null) {
      setLoading(true);
      setError(null);
      configTagsFetchPromise = getJson<StockTag[]>("/api/stocks/config/tags");
      try {
        const data = await configTagsFetchPromise;
        notifyConfigTagsChange(data);
        setConfigTags(data);
      } catch (e) {
        console.error("Failed to fetch config tags", e);
      } finally {
        configTagsFetchPromise = null;
      }
    }

    setLoading(false);
  }, []);

  useEffect(() => {
    stocksChangeListeners.push(setStocks);
    configTagsChangeListeners.push(setConfigTags);

    void fetchAll();

    return () => {
      stocksChangeListeners = stocksChangeListeners.filter((l) => l !== setStocks);
      configTagsChangeListeners = configTagsChangeListeners.filter((l) => l !== setConfigTags);
    };
  }, [fetchAll]);

  // Derive unique tags from the loaded stocks — no extra API call needed
  const allTags = useMemo<StockTag[]>(() => {
    const seen = new Map<string, string>(); // name → color
    for (const stock of stocks) {
      for (const tag of stock.tags) {
        if (!seen.has(tag.name)) {
          seen.set(tag.name, tag.color);
        }
      }
    }
    return Array.from(seen.entries())
      .map(([name, color]) => ({ name, color }))
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [stocks]);

  const filterByTag = useCallback(
    (tagName: string | null): Stock[] => {
      if (!tagName) return stocks;
      return stocks.filter((s) => s.tags.some((t) => t.name === tagName));
    },
    [stocks],
  );

  const createStock = async (payload: CreateStockInput): Promise<Stock> => {
    const created = await postJson<Stock>("/api/stocks", {
      symbol: payload.symbol,
      instrument_token: payload.instrument_token,
      company_name: payload.company_name,
      exchange: payload.exchange,
      notes: payload.notes ?? null,
      priority: payload.priority ?? null,
      tags: payload.tags ?? [],
    });
    const updated = [created, ...(stocks ?? [])];
    notifyStocksChange(updated);
    setStocks(updated);
    return created;
  };

  const updateStock = async (
    stockId: number,
    payload: UpdateStockInput,
  ): Promise<Stock> => {
    const updated = await patchJson<Stock>(`/api/stocks/${stockId}`, payload);
    const newStocks = (stocks ?? []).map((s) => (s.id === stockId ? updated : s));
    notifyStocksChange(newStocks);
    setStocks(newStocks);
    return updated;
  };

  const deleteStock = async (stockId: number): Promise<void> => {
    await deleteJson(`/api/stocks/${stockId}`);
    const newStocks = (stocks ?? []).filter((s) => s.id !== stockId);
    notifyStocksChange(newStocks);
    setStocks(newStocks);
  };

  return {
    stocks,
    allTags,
    configTags,
    loading,
    error,
    refetch: fetchAll,
    filterByTag,
    createStock,
    updateStock,
    deleteStock,
  };
}
