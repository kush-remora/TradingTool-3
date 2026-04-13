import { useCallback, useState } from "react";
import { getJson } from "../utils/api";
import type { LifecycleSummary, LifecycleSymbolDetail } from "../types";

const BASE = "/api/strategy/rsi-momentum/lifecycle";

export function useRsiMomentumLifecycle(profileId: string) {
  const [summary, setSummary] = useState<LifecycleSummary | null>(null);
  const [symbolDetail, setSymbolDetail] = useState<LifecycleSymbolDetail | null>(null);
  const [loadingSummary, setLoadingSummary] = useState(false);
  const [loadingSymbol, setLoadingSymbol] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadSummary = useCallback(
    async (from?: string, to?: string) => {
      setLoadingSummary(true);
      setError(null);
      try {
        const params = new URLSearchParams({ profileId });
        if (from) params.set("from", from);
        if (to) params.set("to", to);
        const result = await getJson<LifecycleSummary>(`${BASE}/summary?${params.toString()}`);
        setSummary(result);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load lifecycle summary");
      } finally {
        setLoadingSummary(false);
      }
    },
    [profileId],
  );

  const loadSymbol = useCallback(
    async (symbol: string, from?: string, to?: string) => {
      setLoadingSymbol(true);
      setError(null);
      try {
        const params = new URLSearchParams({ profileId, symbol });
        if (from) params.set("from", from);
        if (to) params.set("to", to);
        const result = await getJson<LifecycleSymbolDetail>(`${BASE}?${params.toString()}`);
        setSymbolDetail(result);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load lifecycle for symbol");
      } finally {
        setLoadingSymbol(false);
      }
    },
    [profileId],
  );

  return { summary, symbolDetail, loadingSummary, loadingSymbol, error, loadSummary, loadSymbol };
}
