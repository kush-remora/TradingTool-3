import { useState, useCallback } from "react";
import { postJson } from "../utils/api";
import type { FiftyTwoWeekLowBacktestRequest, FiftyTwoWeekLowBacktestResponse } from "../types";

export function use52WeekLowBacktest() {
  const [data, setData] = useState<FiftyTwoWeekLowBacktestResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: FiftyTwoWeekLowBacktestRequest) => {
    setLoading(true);
    setError(null);
    setData(null);

    try {
      const result = await postJson<FiftyTwoWeekLowBacktestResponse>("/api/strategy/52-week-low/backtest", request);
      setData(result);
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("An unknown error occurred during backtest.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
