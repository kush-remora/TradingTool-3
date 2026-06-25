import { useEffect, useMemo, useState } from "react";
import type { StockQuoteSnapshot } from "../types";
import { getJson } from "../utils/api";
import { isIndianEquityMarketOpen } from "../utils/marketHours";

interface UseStockQuotesResult {
  quotesBySymbol: Record<string, StockQuoteSnapshot>;
  loading: boolean;
  error: string | null;
}

function normalizeSymbols(symbols: string[]): string[] {
  return Array.from(
    new Set(
      symbols
        .map((symbol) => symbol.trim().toUpperCase())
        .filter((symbol) => symbol.length > 0),
    ),
  );
}

export function useStockQuotes(symbols: string[]): UseStockQuotesResult {
  const symbolsKey = symbols.join("|");
  const normalizedSymbols = useMemo(() => normalizeSymbols(symbols), [symbolsKey]);
  const [quotesBySymbol, setQuotesBySymbol] = useState<Record<string, StockQuoteSnapshot>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    let pollId: number | null = null;

    if (normalizedSymbols.length === 0) {
      setQuotesBySymbol({});
      setLoading(false);
      setError(null);
      return () => {
        active = false;
      };
    }

    const fetchQuotes = async (showLoading: boolean) => {
      if (showLoading) setLoading(true);
      try {
        const query = encodeURIComponent(normalizedSymbols.join(","));
        const snapshots = await getJson<StockQuoteSnapshot[]>(
          `/api/stocks/quotes?symbols=${query}`,
        );
        if (!active) return;
        const mapped: Record<string, StockQuoteSnapshot> = {};
        snapshots.forEach((snapshot) => {
          mapped[snapshot.symbol.toUpperCase()] = snapshot;
        });
        setQuotesBySymbol(mapped);
        setError(null);
      } catch (e) {
        if (!active) return;
        setError(e instanceof Error ? e.message : "Failed to load quotes");
      } finally {
        if (active && showLoading) setLoading(false);
      }
    };

    const startPolling = () => {
      if (pollId != null) {
        return;
      }

      pollId = window.setInterval(() => {
        if (!isIndianEquityMarketOpen()) {
          if (pollId != null) {
            window.clearInterval(pollId);
            pollId = null;
          }
          return;
        }

        void fetchQuotes(false);
      }, 10_000);
    };

    void fetchQuotes(true);

    if (isIndianEquityMarketOpen()) {
      startPolling();
    }

    return () => {
      active = false;
      if (pollId != null) {
        window.clearInterval(pollId);
      }
    };
  }, [normalizedSymbols]);

  return { quotesBySymbol, loading, error };
}
