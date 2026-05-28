import { message } from "antd";
import { useCallback, useState } from "react";
import type { IntradayShockBacktestRequest, IntradayShockBacktestResponse } from "../types";
import { postJson } from "../utils/api";

export function useIntradayShockBacktest() {
  const [data, setData] = useState<IntradayShockBacktestResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);

  const run = useCallback(async (request: IntradayShockBacktestRequest) => {
    setLoading(true);
    setError(null);
    setData(null);

    try {
      const responseData = await postJson<IntradayShockBacktestResponse>(
        "/api/strategy/intraday-shock/backtest",
        request
      );

      setData(responseData);
      message.success("Backtest completed successfully!");
    } catch (err: unknown) {
      console.error("Backtest failed:", err);
      const e = err instanceof Error ? err : new Error("Unknown error");
      setError(e);
      message.error(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  return {
    data,
    loading,
    error,
    run,
  };
}
