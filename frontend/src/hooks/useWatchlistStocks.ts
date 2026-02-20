import { useEffect, useState } from "react";
import { getJson } from "../utils/api";
import type { Stock, WatchlistStock } from "../types";

// Deterministic mock metric seeded from symbol string
function seedFromSymbol(symbol: string, offset: number): number {
  let hash = offset;
  for (let i = 0; i < symbol.length; i++) {
    hash = (hash * 31 + symbol.charCodeAt(i)) & 0xffff;
  }
  return hash;
}

function mockRsi(symbol: string): number {
  return 30 + (seedFromSymbol(symbol, 1) % 50);
}

function mockMinRsi(symbol: string, days: number): number {
  const base = 20 + (seedFromSymbol(symbol, days) % 30);
  return Math.min(base, mockRsi(symbol));
}

function mockPrice(symbol: string): number {
  return 200 + (seedFromSymbol(symbol, 7) % 3800);
}

function mockDrawdown(symbol: string): number {
  return -((seedFromSymbol(symbol, 3) % 25) + 1);
}

function mockMaxDrawdown(symbol: string): number {
  return mockDrawdown(symbol) - (seedFromSymbol(symbol, 5) % 15);
}

function mockLevel(symbol: string, offset: number): number {
  const base = mockPrice(symbol);
  return +(base * (1 + (offset * (seedFromSymbol(symbol, offset * 2) % 5)) / 100)).toFixed(2);
}

export interface StockRow {
  key: string;
  stockId: number;
  symbol: string;
  companyName: string;
  exchange: string;
  price: number;
  prevClose: number;
  rsi: number;
  rsi15dMin: number;
  rsi100dMin: number;
  rsi200dMin: number;
  r1: number;
  r2: number;
  r3: number;
  meanRevBaseline: number;
  drawdown: number;
  maxDrawdown: number;
}

function toStockRow(stock: Stock): StockRow {
  const price = mockPrice(stock.symbol);
  return {
    key: String(stock.id),
    stockId: stock.id,
    symbol: stock.symbol,
    companyName: stock.companyName,
    exchange: stock.exchange,
    price,
    prevClose: +(price * (1 - (seedFromSymbol(stock.symbol, 9) % 5 - 2) / 100)).toFixed(2),
    rsi: mockRsi(stock.symbol),
    rsi15dMin: mockMinRsi(stock.symbol, 15),
    rsi100dMin: mockMinRsi(stock.symbol, 100),
    rsi200dMin: mockMinRsi(stock.symbol, 200),
    r1: mockLevel(stock.symbol, 1),
    r2: mockLevel(stock.symbol, 2),
    r3: mockLevel(stock.symbol, 3),
    meanRevBaseline: mockLevel(stock.symbol, -1),
    drawdown: mockDrawdown(stock.symbol),
    maxDrawdown: mockMaxDrawdown(stock.symbol),
  };
}

export function useWatchlistStocks(watchlistId: number | null) {
  const [rows, setRows] = useState<StockRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (watchlistId === null) {
      setRows([]);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    async function load() {
      try {
        const [items, allStocks] = await Promise.all([
          getJson<WatchlistStock[]>(`/api/watchlist/lists/${watchlistId}/items`),
          getJson<Stock[]>("/api/watchlist/stocks?limit=500"),
        ]);

        if (cancelled) return;

        const stockMap = new Map<number, Stock>(allStocks.map((s) => [s.id, s]));
        const matched = items
          .map((item) => stockMap.get(item.stockId))
          .filter((s): s is Stock => s !== undefined)
          .map(toStockRow);

        setRows(matched);
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : "Failed to load stocks");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void load();
    return () => { cancelled = true; };
  }, [watchlistId]);

  return { rows, loading, error };
}
