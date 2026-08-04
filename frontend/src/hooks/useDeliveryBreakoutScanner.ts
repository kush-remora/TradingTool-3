import { useCallback, useState } from "react";
import { getJson } from "../utils/api";
import type { DeliveryBreakoutDashboardResponse, UniverseOptionsResponse } from "../types";

const DASHBOARD_PATH = "/api/strategy/delivery-breakout/dashboard";
const WATCHLISTS_PATH = "/api/strategy/weekly-price-review/watchlists";

export function useDeliveryBreakoutScanner() {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [data, setData] = useState<DeliveryBreakoutDashboardResponse | null>(null);
  const [loadingWatchlists, setLoadingWatchlists] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadWatchlists = useCallback(async (): Promise<UniverseOptionsResponse> => {
    setLoadingWatchlists(true);
    setError(null);
    try {
      const result = await getJson<UniverseOptionsResponse>(WATCHLISTS_PATH, { useCache: false });
      setWatchlists(result.options);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load watchlists";
      setError(message);
      throw err;
    } finally {
      setLoadingWatchlists(false);
    }
  }, []);

  const loadDashboard = useCallback(async (watchlistKey: string, tradeDate?: string) => {
    setLoading(true);
    setError(null);

    try {
      const params = new URLSearchParams({ watchlistKey });
      if (tradeDate) {
        params.set("tradeDate", tradeDate);
      }
      const result = await getJson<DeliveryBreakoutDashboardResponse>(
        `${DASHBOARD_PATH}?${params.toString()}`,
        { useCache: false },
      );
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
    watchlists,
    data,
    loadingWatchlists,
    loading,
    error,
    loadWatchlists,
    loadDashboard,
  };
}
