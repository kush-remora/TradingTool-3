import { useCallback, useState } from "react";
import type {
  WeeklyLowLimitDailyValidationRequest,
  WeeklyLowLimitDailyValidationResponse,
} from "../types";
import { postJson } from "../utils/api";

export function useWeeklyLowLimitDailyValidation() {
  const [data, setData] = useState<WeeklyLowLimitDailyValidationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (request: WeeklyLowLimitDailyValidationRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      setData(await postJson<WeeklyLowLimitDailyValidationResponse>("/api/strategy/weekly-low-limit-backtest/daily-validation", request));
    } catch (cause) {
      setData(null);
      setError(cause instanceof Error ? cause.message : "Daily validation failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, load };
}
