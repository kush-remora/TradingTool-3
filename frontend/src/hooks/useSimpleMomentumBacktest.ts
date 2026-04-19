import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type { SimpleMomentumBacktestRequest, SimpleMomentumBacktestResult } from "../types";

const SIMPLE_BACKTEST_PATH = "/api/strategy/rsi-momentum/backtest/simple";

export function useSimpleMomentumBacktest() {
  const [data, setData] = useState<SimpleMomentumBacktestResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: SimpleMomentumBacktestRequest) => {
    setLoading(true);
    setError(null);

    try {
      const result = await postJson<SimpleMomentumBacktestResult>(SIMPLE_BACKTEST_PATH, request);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Simple momentum backtest failed");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
