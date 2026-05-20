import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type { FiftyTwoWeekHighBacktestRequest, FiftyTwoWeekHighBacktestResponse } from "../types";

const PATH = "/api/strategy/52-week-high/backtest";

export function use52WeekHighBacktest() {
  const [data, setData] = useState<FiftyTwoWeekHighBacktestResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: FiftyTwoWeekHighBacktestRequest) => {
    setLoading(true);
    setError(null);
    try {
      const result = await postJson<FiftyTwoWeekHighBacktestResponse>(PATH, request);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "52-week-high backtest failed");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
