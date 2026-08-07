import { useCallback, useState } from "react";
import type { NetwebCycleReport, NetwebCycleRequest } from "../types";
import { postJson } from "../utils/api";

const NETWEB_CYCLE_PATH = "/api/strategy/netweb-cycle/run";

export function useNetwebCycle() {
  const [data, setData] = useState<NetwebCycleReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: NetwebCycleRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      setData(await postJson<NetwebCycleReport>(NETWEB_CYCLE_PATH, request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "NETWEB cycle analysis failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
