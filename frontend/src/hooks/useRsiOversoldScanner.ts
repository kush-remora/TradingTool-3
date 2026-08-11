import { useCallback, useState } from "react";
import type { RsiOversoldScanRequest, RsiOversoldScanResponse } from "../types";
import { postJson } from "../utils/api";

const SCAN_PATH = "/api/strategy/rsi-oversold/scan";

export function useRsiOversoldScanner() {
  const [data, setData] = useState<RsiOversoldScanResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: RsiOversoldScanRequest): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      setData(await postJson<RsiOversoldScanResponse>(SCAN_PATH, request));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "RSI low scan failed.");
    } finally {
      setLoading(false);
    }
  }, []);

  return { data, loading, error, run };
}
