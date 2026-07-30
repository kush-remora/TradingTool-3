import { useCallback, useEffect, useState } from "react";
import type { BreakoutTrackerEntry, SaveBreakoutTrackerEntryRequest } from "../types";
import { deleteJson, getJson, postJson } from "../utils/api";

interface UseBreakoutTrackerResult {
  entries: BreakoutTrackerEntry[];
  loading: boolean;
  error: string | null;
  saveEntry: (entry: SaveBreakoutTrackerEntryRequest) => Promise<boolean>;
  removeEntry: (id: number) => Promise<boolean>;
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

export function useBreakoutTracker(): UseBreakoutTrackerResult {
  const [entries, setEntries] = useState<BreakoutTrackerEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isCurrentRequest = true;
    void getJson<BreakoutTrackerEntry[]>("/api/breakout-tracker", { useCache: false })
      .then((loadedEntries) => {
        if (isCurrentRequest) setEntries(loadedEntries);
      })
      .catch((requestError: unknown) => {
        if (isCurrentRequest) setError(errorMessage(requestError, "Failed to load breakout tracker."));
      })
      .finally(() => {
        if (isCurrentRequest) setLoading(false);
      });

    return () => {
      isCurrentRequest = false;
    };
  }, []);

  const saveEntry = useCallback(async (entry: SaveBreakoutTrackerEntryRequest): Promise<boolean> => {
    try {
      const savedEntry = await postJson<BreakoutTrackerEntry>("/api/breakout-tracker", entry);
      setEntries((currentEntries) => {
        const otherEntries = currentEntries.filter((currentEntry) => currentEntry.id !== savedEntry.id);
        return [...otherEntries, savedEntry].sort((left, right) => right.breakoutDate.localeCompare(left.breakoutDate));
      });
      setError(null);
      return true;
    } catch (requestError: unknown) {
      setError(errorMessage(requestError, "Failed to save breakout tracker entry."));
      return false;
    }
  }, []);

  const removeEntry = useCallback(async (id: number): Promise<boolean> => {
    try {
      await deleteJson(`/api/breakout-tracker/${id}`);
      setEntries((currentEntries) => currentEntries.filter((entry) => entry.id !== id));
      setError(null);
      return true;
    } catch (requestError: unknown) {
      setError(errorMessage(requestError, "Failed to delete breakout tracker entry."));
      return false;
    }
  }, []);

  return { entries, loading, error, saveEntry, removeEntry };
}
