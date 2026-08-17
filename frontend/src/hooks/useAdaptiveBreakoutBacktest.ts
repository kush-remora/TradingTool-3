import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type {
  AdaptiveBreakoutBacktestRequest,
  AdaptiveBreakoutBacktestResponse,
} from "../types";

const RUN_PATH = "/api/strategy/adaptive-breakout/backtest/run";

export function useAdaptiveBreakoutBacktest() {
  const [data, setData] = useState<AdaptiveBreakoutBacktestResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: AdaptiveBreakoutBacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      setData(await postJson<AdaptiveBreakoutBacktestResponse>(RUN_PATH, request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Backtest failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
