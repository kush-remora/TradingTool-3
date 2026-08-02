import { useCallback, useState } from "react";
import type { PriceAcceptanceScanResponse } from "../types";
import { getJson } from "../utils/api";

interface PriceAcceptanceScanRequest {
  indexKey: string;
  asOfDate: string;
}

export function usePriceAcceptanceScanner() {
  const [data, setData] = useState<PriceAcceptanceScanResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: PriceAcceptanceScanRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({
        indexKey: request.indexKey,
        asOfDate: request.asOfDate,
      });
      const response = await getJson<PriceAcceptanceScanResponse>(
        `/api/strategy/price-acceptance/scan?${params.toString()}`,
        { useCache: false },
      );
      setData(response);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Price acceptance scan failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
