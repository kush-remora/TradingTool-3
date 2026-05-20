import { useCallback, useState } from "react";
import { postJson } from "../utils/api";
import type { FiftyTwoWeekHighLiveRequest, FiftyTwoWeekHighLiveResponse, FiftyTwoWeekHighLiveRow, FiftyTwoWeekHighLiveTelegramRequest } from "../types";

const RUN_PATH = "/api/strategy/52-week-high/live/run";
const TELEGRAM_PATH = "/api/strategy/52-week-high/live/telegram";

export function use52WeekHighLive() {
  const [data, setData] = useState<FiftyTwoWeekHighLiveResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const run = useCallback(async (request: FiftyTwoWeekHighLiveRequest) => {
    setLoading(true);
    setError(null);
    try {
      const response = await postJson<FiftyTwoWeekHighLiveResponse>(RUN_PATH, request);
      setData(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "104W live scan failed");
    } finally {
      setLoading(false);
    }
  }, []);

  const sendTelegramForRow = useCallback(async (bucket: string, row: FiftyTwoWeekHighLiveRow): Promise<void> => {
    const payload: FiftyTwoWeekHighLiveTelegramRequest = {
      symbol: row.symbol,
      bucket,
      breakoutLevel: row.breakoutLevel,
      latestHigh: row.latestHigh,
      latestClose: row.latestClose,
      gapToBreakoutPct: row.gapToBreakoutPct,
      latestDate: row.latestDate,
      lastHitDate: row.lastHitDate,
    };
    await postJson(TELEGRAM_PATH, payload);
  }, []);

  return { data, loading, error, run, sendTelegramForRow };
}
