import { useCallback, useState } from "react";
import type { AbsoluteDeliveryBacktestResponse } from "../types";
import { getJson } from "../utils/api";

const BACKTEST_PATH = "/api/strategy/absolute-delivery/backtest";

export function useAbsoluteDeliveryBacktest() {
  const [data, setData] = useState<AbsoluteDeliveryBacktestResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadBacktest = useCallback(async (): Promise<AbsoluteDeliveryBacktestResponse> => {
    setLoading(true);
    setError(null);

    try {
      const result = await getJson<AbsoluteDeliveryBacktestResponse>(BACKTEST_PATH, { useCache: false });
      setData(result);
      return result;
    } catch (loadError) {
      const message = loadError instanceof Error
        ? loadError.message
        : "Failed to load the absolute delivery backtest";
      setError(message);
      throw loadError;
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, loadBacktest };
}
