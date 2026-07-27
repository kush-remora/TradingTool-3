import { useCallback, useState } from "react";
import type {
  AbsoluteDeliveryBacktestResponse,
  AbsoluteDeliveryGroupingOption,
} from "../types";
import { getJson } from "../utils/api";

const BACKTEST_PATH = "/api/strategy/absolute-delivery/backtest";
const GROUPINGS_PATH = "/api/strategy/absolute-delivery/groupings";

export function useAbsoluteDeliveryBacktest() {
  const [data, setData] = useState<AbsoluteDeliveryBacktestResponse | null>(null);
  const [groupings, setGroupings] = useState<AbsoluteDeliveryGroupingOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingGroupings, setLoadingGroupings] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [groupingError, setGroupingError] = useState<string | null>(null);

  const loadGroupings = useCallback(async (): Promise<AbsoluteDeliveryGroupingOption[]> => {
    setLoadingGroupings(true);
    setGroupingError(null);
    try {
      const result = await getJson<AbsoluteDeliveryGroupingOption[]>(GROUPINGS_PATH, { useCache: false });
      setGroupings(result);
      return result;
    } catch (loadError) {
      const message = loadError instanceof Error
        ? loadError.message
        : "Failed to load institutional groupings";
      setGroupingError(message);
      throw loadError;
    } finally {
      setLoadingGroupings(false);
    }
  }, []);

  const loadBacktest = useCallback(async (
    groupingKey: string,
  ): Promise<AbsoluteDeliveryBacktestResponse> => {
    setLoading(true);
    setError(null);
    setData(null);

    try {
      const query = new URLSearchParams({ grouping: groupingKey });
      const result = await getJson<AbsoluteDeliveryBacktestResponse>(
        `${BACKTEST_PATH}?${query.toString()}`,
        { useCache: false },
      );
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

  return {
    data,
    groupings,
    loading,
    loadingGroupings,
    error,
    groupingError,
    loadGroupings,
    loadBacktest,
  };
}
