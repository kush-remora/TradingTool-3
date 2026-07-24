import { useCallback, useState } from "react";
import type {
  WeeklyBaseGroupBacktestReport,
  WeeklyBaseGroupBacktestRequest,
} from "../types";
import { postJson } from "../utils/api";

export function useWeeklyBaseGroupBacktest() {
  const [data, setData] = useState<WeeklyBaseGroupBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = useCallback(
    async (request: WeeklyBaseGroupBacktestRequest): Promise<void> => {
      setLoading(true);
      setError(null);
      setData(null);
      try {
        setData(
          await postJson<WeeklyBaseGroupBacktestReport>(
            "/api/strategy/weekly-base-group-backtest/run",
            request,
          ),
        );
      } catch (cause) {
        setError(
          cause instanceof Error ? cause.message : "Group backtest failed.",
        );
      } finally {
        setLoading(false);
      }
    },
    [],
  );
  return { data, loading, error, run };
}
