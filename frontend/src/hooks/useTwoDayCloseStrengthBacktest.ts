import { useCallback, useState } from "react";
import type {
  TwoDayCloseStrengthBacktestReport,
  TwoDayCloseStrengthBacktestRequest,
} from "../types";
import { postJson } from "../utils/api";

export function useTwoDayCloseStrengthBacktest() {
  const [data, setData] = useState<TwoDayCloseStrengthBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: TwoDayCloseStrengthBacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    setData(null);
    try {
      setData(await postJson<TwoDayCloseStrengthBacktestReport>("/api/strategy/two-day-close-strength-backtest/run", request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Two-day close-strength backtest failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
