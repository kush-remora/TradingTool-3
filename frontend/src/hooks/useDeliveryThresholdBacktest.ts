import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type { DeliveryThresholdBacktestRequest, DeliveryThresholdBacktestResponse } from "../types";

const DELIVERY_THRESHOLD_BACKTEST_PATH = "/api/strategy/delivery-threshold/backtest";

export function useDeliveryThresholdBacktest() {
  const [data, setData] = useState<DeliveryThresholdBacktestResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: DeliveryThresholdBacktestRequest) => {
    setLoading(true);
    setError(null);

    try {
      const result = await postJson<DeliveryThresholdBacktestResponse>(DELIVERY_THRESHOLD_BACKTEST_PATH, request);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delivery threshold backtest failed");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
