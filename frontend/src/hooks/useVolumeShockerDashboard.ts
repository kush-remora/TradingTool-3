import { useCallback, useState } from "react";
import { getJson } from "../utils/api";
import type {
  VolumeShockerDashboardResponse,
  VolumeShockerDatesResponse,
  VolumeShockerDetailResponse,
} from "../types";

const DATES_PATH = "/api/strategy/volume-shocker/dates";

export function useVolumeShockerDashboard() {
  const [dates, setDates] = useState<VolumeShockerDatesResponse | null>(null);
  const [data, setData] = useState<VolumeShockerDashboardResponse | null>(null);
  const [loadingDates, setLoadingDates] = useState(false);
  const [loadingData, setLoadingData] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadDates = useCallback(async () => {
    setLoadingDates(true);
    setError(null);
    try {
      const result = await getJson<VolumeShockerDatesResponse>(DATES_PATH, { useCache: false });
      setDates(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load volume-shocker dates";
      setError(message);
      throw err;
    } finally {
      setLoadingDates(false);
    }
  }, []);

  const loadDashboard = useCallback(async (tradeDate: string) => {
    setLoadingData(true);
    setError(null);
    try {
      const result = await getJson<VolumeShockerDashboardResponse>(
        `/api/strategy/volume-shocker/dashboard?tradeDate=${encodeURIComponent(tradeDate)}`,
        { useCache: false },
      );
      setData(result);
      return result;
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to load volume-shocker dashboard";
      setError(message);
      throw err;
    } finally {
      setLoadingData(false);
    }
  }, []);

  const loadDetail = useCallback(async (tradeDate: string, symbol: string) => {
    return getJson<VolumeShockerDetailResponse>(
      `/api/strategy/volume-shocker/detail?tradeDate=${encodeURIComponent(tradeDate)}&symbol=${encodeURIComponent(symbol)}`,
      { useCache: false },
    );
  }, []);

  return {
    dates,
    data,
    loadingDates,
    loadingData,
    error,
    loadDates,
    loadDashboard,
    loadDetail,
  };
}
