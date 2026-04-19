import { useState } from "react";
import type { RsiRankDriftBacktestReport, RsiRankDriftBacktestRequest } from "../types";
import { postJson } from "../utils/api";

const BACKTEST_PATH = "/api/strategy/rsi-momentum/backtest/rank-drift";

export function useRsiRankDriftBacktest() {
  const [report, setReport] = useState<RsiRankDriftBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const runBacktest = async (request: RsiRankDriftBacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      const data = await postJson<RsiRankDriftBacktestReport>(BACKTEST_PATH, request);
      setReport(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to run RSI rank drift backtest");
    } finally {
      setLoading(false);
    }
  };

  return { report, loading, error, runBacktest };
}
