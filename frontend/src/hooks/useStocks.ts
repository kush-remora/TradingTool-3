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
  allTags: StockTag[];           // unique tags derived from all stocks — for dropdown
  loading: boolean;
  error: string | null;
  refetch: () => Promise<void>;
  filterByTag: (tagName: string | null) => Stock[];
  createStock: (payload: CreateStockInput) => Promise<Stock>;
  updateStock: (stockId: number, payload: UpdateStockInput) => Promise<Stock>;
  deleteStock: (stockId: number) => Promise<void>;
}

export function useStocks(): UseStocksResult {
  const [stocks, setStocks] = useState<Stock[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getJson<Stock[]>("/api/stocks");
      setStocks(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to fetch stocks");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchAll();
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
    setStocks((prev) => [created, ...prev]);
    return created;
  };

  const updateStock = async (
    stockId: number,
    payload: UpdateStockInput,
  ): Promise<Stock> => {
    const updated = await patchJson<Stock>(`/api/stocks/${stockId}`, payload);
    setStocks((prev) => prev.map((s) => (s.id === stockId ? updated : s)));
    return updated;
  };

  const deleteStock = async (stockId: number): Promise<void> => {
    await deleteJson(`/api/stocks/${stockId}`);
    setStocks((prev) => prev.filter((s) => s.id !== stockId));
  };

  return {
    stocks,
    allTags,
    loading,
    error,
    refetch: fetchAll,
    filterByTag,
    createStock,
    updateStock,
    deleteStock,
  };
}
