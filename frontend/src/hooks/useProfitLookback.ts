import { useState } from "react";
import type {
  ProfitLookbackRequest,
  ProfitLookbackResponse,
  ProfitLookbackBulkRequest,
  ProfitLookbackBulkResponse,
} from "../types";
import { postJson } from "../utils/api";

const PROFIT_LOOKBACK_PATH = "/api/strategy/profit-lookback";
const PROFIT_LOOKBACK_BULK_PATH = "/api/strategy/profit-lookback/bulk";

export function useProfitLookback() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const runProfitLookback = async (request: ProfitLookbackRequest): Promise<ProfitLookbackResponse> => {
    setLoading(true);
    setError(null);
    try {
      return await postJson<ProfitLookbackResponse>(PROFIT_LOOKBACK_PATH, request);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to run profit lookback analysis";
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const runProfitLookbackBulk = async (request: ProfitLookbackBulkRequest): Promise<ProfitLookbackBulkResponse> => {
    setLoading(true);
    setError(null);
    try {
      return await postJson<ProfitLookbackBulkResponse>(PROFIT_LOOKBACK_BULK_PATH, request);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Failed to run bulk profit lookback analysis";
      setError(message);
      throw err;
    } finally {
      setLoading(false);
    }
  };

  return { loading, error, runProfitLookback, runProfitLookbackBulk };
}
