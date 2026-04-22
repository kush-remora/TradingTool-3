import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type { VolumeSpikeBacktestRequest, VolumeSpikeBacktestResponse } from "../types";

const VOLUME_SPIKE_BACKTEST_PATH = "/api/strategy/volume-spike/backtest";

export function useVolumeSpikeBacktest() {
  const [data, setData] = useState<VolumeSpikeBacktestResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: VolumeSpikeBacktestRequest) => {
    setLoading(true);
    setError(null);

    try {
      const result = await postJson<VolumeSpikeBacktestResponse>(VOLUME_SPIKE_BACKTEST_PATH, request);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Volume spike backtest failed");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
