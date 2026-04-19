import { useState, useCallback } from "react";
import { getJson } from "../utils/api";
import type { SwingAnalysisResponse } from "../types";

export function useSwingAnalysis() {
  const [data, setData] = useState<SwingAnalysisResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchSwings = useCallback(async (symbol: string, reversal: number, lookback: number) => {
    setLoading(true);
    setError(null);
    try {
      const result = await getJson<SwingAnalysisResponse>(
        `/api/analysis/swings/${symbol}?reversal=${reversal}&lookback=${lookback}`
      );
      setData(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to fetch swing analysis");
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, fetchSwings };
}
