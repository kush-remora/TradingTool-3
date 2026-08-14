import { useCallback, useState } from "react";
import type {
  FridayCloseStrengthBacktestReport,
  FridayCloseStrengthBacktestRequest,
} from "../types";
import { postJson } from "../utils/api";

export function useFridayCloseStrengthBacktest() {
  const [data, setData] = useState<FridayCloseStrengthBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: FridayCloseStrengthBacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    setData(null);
    try {
      setData(await postJson<FridayCloseStrengthBacktestReport>("/api/strategy/friday-close-strength-backtest/run", request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Friday close-strength backtest failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
