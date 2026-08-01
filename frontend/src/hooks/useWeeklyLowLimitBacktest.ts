import { useCallback, useState } from "react";
import type {
  WeeklyLowLimitBacktestReport,
  WeeklyLowLimitBacktestRequest,
} from "../types";
import { postJson } from "../utils/api";

export function useWeeklyLowLimitBacktest() {
  const [data, setData] = useState<WeeklyLowLimitBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: WeeklyLowLimitBacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    setData(null);
    try {
      setData(await postJson<WeeklyLowLimitBacktestReport>("/api/strategy/weekly-low-limit-backtest/run", request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Weekly low limit backtest failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
