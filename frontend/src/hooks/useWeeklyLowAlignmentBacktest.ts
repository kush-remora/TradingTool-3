import { useCallback, useState } from "react";
import type {
  WeeklyLowAlignmentBacktestReport,
  WeeklyLowAlignmentBacktestRequest,
} from "../types";
import { postJson } from "../utils/api";

export function useWeeklyLowAlignmentBacktest() {
  const [data, setData] = useState<WeeklyLowAlignmentBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: WeeklyLowAlignmentBacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    setData(null);
    try {
      setData(await postJson<WeeklyLowAlignmentBacktestReport>("/api/strategy/weekly-low-alignment-backtest/run", request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Weekly low alignment backtest failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
