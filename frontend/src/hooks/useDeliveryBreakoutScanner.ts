import { useCallback, useState } from "react";
import { getJson } from "../utils/api";
import type { DeliveryBreakoutDashboardResponse } from "../types";

const DASHBOARD_PATH = "/api/strategy/delivery-breakout/dashboard";

export function useDeliveryBreakoutScanner() {
  const [data, setData] = useState<DeliveryBreakoutDashboardResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadDashboard = useCallback(async (tradeDate?: string) => {
    setLoading(true);
    setError(null);

    try {
      const path = tradeDate
        ? `${DASHBOARD_PATH}?tradeDate=${encodeURIComponent(tradeDate)}`
        : DASHBOARD_PATH;
      
      const result = await getJson<DeliveryBreakoutDashboardResponse>(path, { useCache: false });
      setData(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load delivery-breakout dashboard";
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
    loadDashboard,
  };
}
