import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type {
  BollingerMeanReversionBacktestRequest,
  BollingerMeanReversionBacktestResponse,
} from "../types";

const BOLLINGER_MEAN_REVERSION_BACKTEST_PATH = "/api/strategy/bollinger/mean-reversion/backtest";

export function useBollingerMeanReversionBacktest() {
  const [data, setData] = useState<BollingerMeanReversionBacktestResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: BollingerMeanReversionBacktestRequest) => {
    setLoading(true);
    setError(null);

    try {
      const result = await postJson<BollingerMeanReversionBacktestResponse>(
        BOLLINGER_MEAN_REVERSION_BACKTEST_PATH,
        request,
      );
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Bollinger mean reversion backtest failed");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
