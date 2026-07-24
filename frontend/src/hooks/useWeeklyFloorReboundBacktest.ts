import { useCallback, useState } from "react";
import type { WeeklyFloorReboundReport, WeeklyFloorReboundRequest } from "../types";
import { postJson } from "../utils/api";

const WEEKLY_FLOOR_REBOUND_PATH = "/api/strategy/weekly-floor-rebound/backtest";

export function useWeeklyFloorReboundBacktest() {
  const [data, setData] = useState<WeeklyFloorReboundReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: WeeklyFloorReboundRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    setData(null);
    try {
      setData(await postJson<WeeklyFloorReboundReport>(WEEKLY_FLOOR_REBOUND_PATH, request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Weekly floor rebound backtest failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
