import { useEffect, useState } from "react";
import type { StockNote } from "../types";
import { deleteJson, getJson, postJson } from "../utils/api";

interface UseInstrumentNotesResult {
  notes: StockNote[];
  loading: boolean;
  error: string | null;
  addNote: (notes: string) => Promise<boolean>;
  removeNote: (id: number) => Promise<boolean>;
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

export function useInstrumentNotes(instrumentToken: number | null): UseInstrumentNotesResult {
  const [notes, setNotes] = useState<StockNote[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (instrumentToken == null) {
      setNotes([]);
      setLoading(false);
      setError(null);
      return;
    }

    let isCurrentRequest = true;
    setNotes([]);
    setLoading(true);
    setError(null);
    void getJson<StockNote[]>(`/api/stocks/notes/${instrumentToken}`, { useCache: false })
      .then((loadedNotes) => {
        if (isCurrentRequest) setNotes(loadedNotes);
      })
      .catch((requestError: unknown) => {
        if (isCurrentRequest) setError(errorMessage(requestError, "Failed to load notes"));
      })
      .finally(() => {
        if (isCurrentRequest) setLoading(false);
      });

    return () => {
      isCurrentRequest = false;
    };
  }, [instrumentToken]);

  const addNote = async (noteText: string): Promise<boolean> => {
    if (instrumentToken == null || !noteText.trim()) return false;

    try {
      const note = await postJson<StockNote>("/api/stocks/notes", {
        instrumentToken,
        notes: noteText.trim(),
      });
      setNotes((currentNotes) => [note, ...currentNotes]);
      setError(null);
      return true;
    } catch (requestError) {
      setError(errorMessage(requestError, "Failed to save note"));
      return false;
    }
  };

  const removeNote = async (id: number): Promise<boolean> => {
    try {
      await deleteJson(`/api/stocks/notes/${id}`);
      setNotes((currentNotes) => currentNotes.filter((note) => note.id !== id));
      setError(null);
      return true;
    } catch (requestError) {
      setError(errorMessage(requestError, "Failed to delete note"));
      return false;
    }
  };

  return { notes, loading, error, addNote, removeNote };
}
