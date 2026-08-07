import { useCallback, useState } from "react";
import type {
  TwoDayGreenCandleBacktestReport,
  TwoDayGreenCandleBacktestRequest,
} from "../types";
import { postJson } from "../utils/api";

const RUN_PATH = "/api/strategy/two-day-green-candle-backtest/run";

export function useTwoDayGreenCandleBacktest() {
  const [data, setData] = useState<TwoDayGreenCandleBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: TwoDayGreenCandleBacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      setData(await postJson<TwoDayGreenCandleBacktestReport>(RUN_PATH, request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Two-day green candle backtest failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
