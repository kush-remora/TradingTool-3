import { useCallback, useEffect, useMemo, useState } from "react";
import { clearCache, getJson } from "../utils/api";
import type { LeadersDrawdownResponse } from "../types";

const PATH = "/api/strategy/rsi-momentum/leaders-drawdown";

export interface LeadersDrawdownQuery {
  fromDate: string;
  toDate: string;
  topN: number;
}

function toQueryString(query: LeadersDrawdownQuery): string {
  const params = new URLSearchParams();
  params.set("from", query.fromDate);
  params.set("to", query.toDate);
  params.set("topN", String(query.topN));
  return params.toString();
}

export function useRsiMomentumLeadersDrawdown(initialQuery: LeadersDrawdownQuery) {
  const [query, setQuery] = useState<LeadersDrawdownQuery>(initialQuery);
  const [data, setData] = useState<LeadersDrawdownResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const requestPath = useMemo(() => `${PATH}?${toQueryString(query)}`, [query]);

  const load = useCallback(async (options?: { force?: boolean }) => {
    setLoading(true);
    setError(null);
    try {
      if (options?.force) {
        clearCache(requestPath);
      }
      const response = await getJson<LeadersDrawdownResponse>(requestPath, {
        useCache: !options?.force,
        ttl: 30_000,
      });
      setData(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load leaders drawdown dashboard");
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [requestPath]);

  useEffect(() => {
    void load();
  }, [load]);

  const reload = useCallback(async () => {
    await load({ force: true });
  }, [load]);

  return {
    query,
    setQuery,
    data,
    loading,
    error,
    reload,
  };
}
