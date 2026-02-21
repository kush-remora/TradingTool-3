import { useEffect, useState } from "react";
import { getJson } from "../utils/api";
import type { InstrumentSearchResult } from "../types";

/**
 * Load all NSE instruments once at hook mount for client-side search.
 * Returns the full list; filtering happens in the component via AutoComplete.filterOption.
 */
export function useInstrumentSearch() {
  const [allInstruments, setAllInstruments] = useState<InstrumentSearchResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchInstruments = async () => {
      try {
        const data = await getJson<InstrumentSearchResult[]>("/api/instruments/all");
        setAllInstruments(data);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to load instruments");
        setAllInstruments([]);
      } finally {
        setLoading(false);
      }
    };

    fetchInstruments();
  }, []);

  return { allInstruments, loading, error };
}
