import { useCallback, useState } from "react";
import { getJson } from "../utils/api";
import type {
  DeliveryBreakoutDashboardResponse,
  DeliveryBreakoutMultiDashboardResponse,
  UniverseOptionsResponse,
} from "../types";

const DASHBOARD_PATH = "/api/strategy/delivery-breakout/dashboard";
const MULTI_DASHBOARD_PATH = "/api/strategy/delivery-breakout/dashboard/multi";
const WATCHLISTS_PATH = "/api/strategy/weekly-price-review/watchlists";

export function useDeliveryBreakoutScanner() {
  const [watchlists, setWatchlists] = useState<UniverseOptionsResponse["options"]>([]);
  const [data, setData] = useState<DeliveryBreakoutDashboardResponse | null>(null);
  const [multiData, setMultiData] = useState<DeliveryBreakoutMultiDashboardResponse | null>(null);
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
      setMultiData(null);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load delivery-breakout dashboard";
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const loadDashboards = useCallback(async (watchlistKeys: string[], tradeDate?: string): Promise<DeliveryBreakoutMultiDashboardResponse> => {
    setLoading(true);
    setError(null);

    try {
      const params = new URLSearchParams({ watchlistKeys: watchlistKeys.join(",") });
      if (tradeDate) {
        params.set("tradeDate", tradeDate);
      }
      const result = await getJson<DeliveryBreakoutMultiDashboardResponse>(
        `${MULTI_DASHBOARD_PATH}?${params.toString()}`,
        { useCache: false },
      );
      setMultiData(result);
      setData(null);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load delivery-breakout dashboards";
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  }, []);

  const clear = useCallback((): void => {
    setData(null);
    setMultiData(null);
    setError(null);
  }, []);

  return {
    watchlists,
    data,
    multiData,
    loadingWatchlists,
    loading,
    error,
    loadWatchlists,
    loadDashboard,
    loadDashboards,
    clear,
  };
}
