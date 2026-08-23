import { useCallback, useRef, useState } from "react";
import type { EmaContinuationWatchlistStudyResponse } from "../types";
import { postJson } from "../utils/api";

interface EmaContinuationWatchlistStudyRequest {
  watchlistKey: string;
}

export function useEmaContinuationWatchlistStudy() {
  const [data, setData] = useState<EmaContinuationWatchlistStudyResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestVersion = useRef(0);

  const run = useCallback(async (request: EmaContinuationWatchlistStudyRequest): Promise<void> => {
    const version = requestVersion.current + 1;
    requestVersion.current = version;
    setLoading(true);
    setError(null);
    try {
      const response = await postJson<EmaContinuationWatchlistStudyResponse>(
        "/api/strategy/ema-continuation/watchlist-study",
        request,
      );
      if (requestVersion.current === version) setData(response);
    } catch (cause) {
      if (requestVersion.current === version) {
        setError(cause instanceof Error ? cause.message : "Unable to run the watchlist EMA study.");
      }
    } finally {
      if (requestVersion.current === version) setLoading(false);
    }
  }, []);

  const clear = useCallback((): void => {
    requestVersion.current += 1;
    setData(null);
    setError(null);
    setLoading(false);
  }, []);

  return { data, loading, error, run, clear };
}
