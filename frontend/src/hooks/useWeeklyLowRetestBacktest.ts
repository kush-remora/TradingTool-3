import { useCallback, useState } from "react";
import type {
  WeeklyLowRetestBacktestReport,
  WeeklyLowRetestBacktestRequest,
} from "../types";
import { postJson } from "../utils/api";

export function useWeeklyLowRetestBacktest() {
  const [data, setData] = useState<WeeklyLowRetestBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: WeeklyLowRetestBacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    setData(null);
    try {
      setData(await postJson<WeeklyLowRetestBacktestReport>("/api/strategy/weekly-low-retest-backtest/run", request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Daily low trigger backtest failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
