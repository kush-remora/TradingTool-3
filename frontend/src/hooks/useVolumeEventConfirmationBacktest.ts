import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type {
  VolumeEventConfirmationBacktestReport,
  VolumeEventConfirmationBacktestRequest,
} from "../types";

const BACKTEST_PATH = "/api/strategy/volume-event-confirmation-backtest/run";

export function useVolumeEventConfirmationBacktest() {
  const [data, setData] = useState<VolumeEventConfirmationBacktestReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: VolumeEventConfirmationBacktestRequest): Promise<void> => {
    setLoading(true);
    setError(null);

    try {
      setData(await postJson<VolumeEventConfirmationBacktestReport>(BACKTEST_PATH, request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Volume-event backtest failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
