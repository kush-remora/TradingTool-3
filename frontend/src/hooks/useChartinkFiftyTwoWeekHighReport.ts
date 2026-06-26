import { useCallback, useState } from "react";
import { getJson } from "../utils/api";
import type { ChartinkFiftyTwoWeekHighBacktestReport } from "../types";

const PATH = "/api/strategy/chartink-fiftytwo-week-high/report";

export function useChartinkFiftyTwoWeekHighReport() {
  const [data, setData] = useState<ChartinkFiftyTwoWeekHighBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadReport = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getJson<ChartinkFiftyTwoWeekHighBacktestReport>(PATH, { useCache: false });
      setData(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load Chartink 52-week-high report";
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  return {
    data,
    loading,
    error,
    loadReport,
  };
}
