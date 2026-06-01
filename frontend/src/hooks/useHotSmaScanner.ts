import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type { HotSmaRow, HotSmaRunRequest, HotSmaRunResponse, HotSmaTelegramRequest } from "../types";

const RUN_PATH = "/api/strategy/hot-sma/run";
const TELEGRAM_PATH = "/api/strategy/hot-sma/telegram";

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

  const sendTelegramForRow = useCallback(async (indexKey: string, row: HotSmaRow): Promise<void> => {
    const payload: HotSmaTelegramRequest = {
      indexKey,
      symbol: row.symbol,
      signalTag: row.signalTag,
      currentPrice: row.currentPrice,
      sma50: row.sma50,
      sma100: row.sma100,
      sma200: row.sma200,
      pctToSma50: row.pctToSma50,
      pctToSma100: row.pctToSma100,
      pctToSma200: row.pctToSma200,
      rsi14: row.rsi14,
    };
    await postJson(TELEGRAM_PATH, payload);
  }, []);

  return { data, loading, error, run, sendTelegramForRow };
}
