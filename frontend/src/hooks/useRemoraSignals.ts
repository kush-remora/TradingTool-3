import { useEffect, useState } from "react";
import type { RemoraEnvelope, RemoraSignal } from "../types";
import { apiBaseUrl } from "../utils/api";

export function useRemoraSignals(type?: "ACCUMULATION" | "DISTRIBUTION") {
  const [data, setData] = useState<RemoraEnvelope | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const url = new URL(`${apiBaseUrl}/api/watchlist/remora`);
    if (type) url.searchParams.set("type", type);

    setLoading(true);
    fetch(url.toString())
      .then((res) => {
        if (!res.ok) throw new Error(`Failed to fetch Remora signals: ${res.statusText}`);
        return res.json();
      })
      .then((data: RemoraEnvelope) => {
        setData(data);
        setError(null);
      })
      .catch((err) => setError(err.message || "Failed to load Remora signals"))
      .finally(() => setLoading(false));
  }, [type]);

  return { 
    signals: data?.signals || [], 
    asOfDate: data?.as_of_date,
    isStale: data?.is_stale,
    staleReason: data?.stale_reason,
    loading, 
    error 
  };
}
