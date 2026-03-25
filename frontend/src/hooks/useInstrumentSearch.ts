import { useEffect, useState } from "react";
import { getJson } from "../utils/api";
import type { InstrumentSearchResult } from "../types";

// Module-level cache: survives component remounts
let cachedInstruments: InstrumentSearchResult[] | null = null;
let fetchPromise: Promise<InstrumentSearchResult[]> | null = null;

/**
 * Load all NSE instruments once at application startup for client-side search.
 * Uses module-level cache to prevent refetch when component remounts.
 * Returns the full list; filtering happens in the component via AutoComplete.filterOption.
 */
export function useInstrumentSearch() {
  const [allInstruments, setAllInstruments] = useState<InstrumentSearchResult[]>(cachedInstruments ?? []);
  const [loading, setLoading] = useState(cachedInstruments === null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // If already cached, use it immediately
    if (cachedInstruments !== null) {
      setAllInstruments(cachedInstruments);
      setLoading(false);
      return;
    }

    // If a fetch is already in progress, wait for it
    if (fetchPromise !== null) {
      fetchPromise
        .then((data) => {
          setAllInstruments(data);
          setLoading(false);
        })
        .catch((err) => {
          setError(err instanceof Error ? err.message : "Failed to load instruments");
          setLoading(false);
        });
      return;
    }

    // Start a new fetch
    fetchPromise = getJson<InstrumentSearchResult[]>("/api/stocks/instruments");
    fetchPromise
      .then((data) => {
        cachedInstruments = data;
        setAllInstruments(data);
        setError(null);
        setLoading(false);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : "Failed to load instruments");
        setAllInstruments([]);
        setLoading(false);
      })
      .finally(() => {
        fetchPromise = null;
      });
  }, []);

  return { allInstruments, loading, error };
}
