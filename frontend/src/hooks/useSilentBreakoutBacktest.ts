import { useCallback, useState } from "react";

import type { SilentBreakoutBacktestResponse } from "../types";
import { postJson } from "../utils/api";

const PATH = "/api/strategy/silent-breakout-backtest/run";

export function useSilentBreakoutBacktest() {
  const [data, setData] = useState<SilentBreakoutBacktestResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (csvContent: string, targetPct: number): Promise<SilentBreakoutBacktestResponse> => {
    setLoading(true);
    setError(null);
    try {
      const response = await postJson<SilentBreakoutBacktestResponse>(PATH, { csvContent, targetPct });
      setData(response);
      return response;
    } catch (caughtError) {
      const message = caughtError instanceof Error ? caughtError.message : "Could not run silent breakout backtest.";
      setError(message);
      throw caughtError;
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
