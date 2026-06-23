import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type { HotSmaRunRequest, HotSmaRunResponse } from "../types";

const RUN_PATH = "/api/strategy/hot-sma/run";

export function useHotSmaScanner() {
  const [data, setData] = useState<HotSmaRunResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: HotSmaRunRequest) => {
    setLoading(true);
    setError(null);
    try {
      const response = await postJson<HotSmaRunResponse>(RUN_PATH, request);
      setData(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Hot SMA scan failed");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
