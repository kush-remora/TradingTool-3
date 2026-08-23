import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type {
  FourDayCloseStrengthBacktestRequest,
  FourDayCloseStrengthBacktestResponse,
} from "../types";

const RUN_PATH = "/api/strategy/four-day-close-strength-backtest/run";

export function useFourDayCloseStrengthBacktest() {
  const [data, setData] = useState<FourDayCloseStrengthBacktestResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: FourDayCloseStrengthBacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    setData(null);
    try {
      setData(await postJson<FourDayCloseStrengthBacktestResponse>(RUN_PATH, request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Four-day close-strength backtest failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
