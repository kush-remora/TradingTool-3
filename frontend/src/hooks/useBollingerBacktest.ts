import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type { BollingerBacktestRequest, BollingerBacktestResponse } from "../types";

const BOLLINGER_BACKTEST_PATH = "/api/strategy/bollinger/backtest";

export function useBollingerBacktest() {
  const [data, setData] = useState<BollingerBacktestResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: BollingerBacktestRequest) => {
    setLoading(true);
    setError(null);

    try {
      const result = await postJson<BollingerBacktestResponse>(BOLLINGER_BACKTEST_PATH, request);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Bollinger backtest failed");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
