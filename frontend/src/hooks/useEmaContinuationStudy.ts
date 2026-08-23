import { useCallback, useState } from "react";
import type { EmaContinuationStudyResponse } from "../types";
import { postJson } from "../utils/api";

interface EmaContinuationStudyRequest {
  watchlistKey: string;
  symbol: string;
  instrumentToken: number;
}

export function useEmaContinuationStudy() {
  const [data, setData] = useState<EmaContinuationStudyResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: EmaContinuationStudyRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      const response = await postJson<EmaContinuationStudyResponse>(
        "/api/strategy/ema-continuation/study",
        request,
      );
      setData(response);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to run EMA continuation study.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
