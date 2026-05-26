import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type { WyckoffPhase1RunRequest, WyckoffPhase1RunResponse } from "../types";

const WYCKOFF_PHASE1_RUN_PATH = "/api/strategy/wyckoff/phase1/run";

export function useWyckoffPhase1Scanner() {
  const [data, setData] = useState<WyckoffPhase1RunResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: WyckoffPhase1RunRequest) => {
    setLoading(true);
    setError(null);

    try {
      const result = await postJson<WyckoffPhase1RunResponse>(WYCKOFF_PHASE1_RUN_PATH, request);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Wyckoff Phase-1 run failed");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
