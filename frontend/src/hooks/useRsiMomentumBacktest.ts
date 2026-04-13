import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type { BacktestResult, StatefulBacktestConfig } from "../types";

const BACKTEST_PATH = "/api/strategy/rsi-momentum/backtest";

interface BacktestParams {
  profileId: string;
  fromDate?: string;
  toDate?: string;
  initialCapital?: number;
  transactionCostBps?: number;
  topN?: number;
  statefulConfig?: StatefulBacktestConfig;
}

export function useRsiMomentumBacktest() {
  const [data, setData] = useState<BacktestResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (params: BacktestParams) => {
    setLoading(true);
    setError(null);
    try {
      const result = await postJson<BacktestResult>(BACKTEST_PATH, params);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Backtest failed");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
