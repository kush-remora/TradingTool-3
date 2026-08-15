import { useCallback, useState } from "react";
import type { BaseRetestBacktestReport, BaseRetestBacktestRequest } from "../types";
import { postJson } from "../utils/api";

export function useBaseRetestBacktest() {
  const [data, setData] = useState<BaseRetestBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: BaseRetestBacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    setData(null);
    try {
      setData(await postJson<BaseRetestBacktestReport>("/api/strategy/base-retest-backtest/run", request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Three-touch base backtest failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
