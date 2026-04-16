import { useState } from "react";
import type { RsiMomentumBacktestReport, RsiMomentumBacktestRequest } from "../types";
import { postJson } from "../utils/api";

const BACKTEST_PATH = "/api/strategy/rsi-momentum/backtest/sniper";

export function useRsiSniperBacktest() {
  const [report, setReport] = useState<RsiMomentumBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const runBacktest = async (request: RsiMomentumBacktestRequest) => {
    setLoading(true);
    setError(null);
    try {
      const data = await postJson<RsiMomentumBacktestReport>(BACKTEST_PATH, request);
      setReport(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to run backtest");
    } finally {
      setLoading(false);
    }
  };

  return { report, loading, error, runBacktest };
}
