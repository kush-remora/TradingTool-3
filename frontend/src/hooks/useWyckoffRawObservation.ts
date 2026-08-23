import { useCallback, useState } from "react";
import type { WyckoffRawObservationResponse } from "../types";
import { getJson } from "../utils/api";

export interface WyckoffRawObservationRequest {
  watchlist?: string;
  symbol?: string;
  days: number;
}

export function useWyckoffRawObservation() {
  const [data, setData] = useState<WyckoffRawObservationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: WyckoffRawObservationRequest): Promise<void> => {
    const params = new URLSearchParams({ days: String(request.days) });
    if (request.watchlist) params.set("watchlist", request.watchlist);
    if (request.symbol) params.set("symbol", request.symbol);

    setLoading(true);
    setError(null);
    try {
      const result = await getJson<WyckoffRawObservationResponse>(
        `/api/strategy/wyckoff/observation?${params.toString()}`,
        { useCache: false },
      );
      setData(result);
    } catch (requestError: unknown) {
      setError(requestError instanceof Error ? requestError.message : "Wyckoff observation failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
