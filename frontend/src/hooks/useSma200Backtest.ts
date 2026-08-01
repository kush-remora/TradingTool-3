import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type { Sma200BacktestRequest, Sma200BacktestResponse } from "../types";

const RUN_PATH = "/api/strategy/sma200-backtest/run";

export function useSma200Backtest() {
  const [data, setData] = useState<Sma200BacktestResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: Sma200BacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      setData(await postJson<Sma200BacktestResponse>(RUN_PATH, request));
    } catch (err) {
      setError(err instanceof Error ? err.message : "SMA200 backtest failed");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
