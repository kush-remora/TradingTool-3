import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { StockNote } from "../types";
import { useInstrumentNotes } from "./useInstrumentNotes";

const deleteJsonMock = vi.fn();
const getJsonMock = vi.fn();
const postJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  deleteJson: (...args: unknown[]) => deleteJsonMock(...args),
  getJson: (...args: unknown[]) => getJsonMock(...args),
  postJson: (...args: unknown[]) => postJsonMock(...args),
}));

describe("useInstrumentNotes", () => {
  it("loads notes and keeps the header list in sync after adding or deleting one", async () => {
    getJsonMock.mockResolvedValue([note(1, "Original note")]);
    postJsonMock.mockResolvedValue(note(2, "New note"));
    deleteJsonMock.mockResolvedValue({ success: true });
    const { result } = renderHook(() => useInstrumentNotes(738561));

    await waitFor(() => expect(result.current.notes).toHaveLength(1));

    await act(async () => {
      expect(await result.current.addNote("New note")).toBe(true);
    });
    expect(postJsonMock).toHaveBeenCalledWith("/api/stocks/notes", { instrumentToken: 738561, notes: "New note" });
    expect(result.current.notes.map((currentNote) => currentNote.notes)).toEqual(["New note", "Original note"]);

    await act(async () => {
      expect(await result.current.removeNote(2)).toBe(true);
    });
    expect(deleteJsonMock).toHaveBeenCalledWith("/api/stocks/notes/2");
    expect(result.current.notes.map((currentNote) => currentNote.notes)).toEqual(["Original note"]);
  });
});

function note(id: number, notes: string): StockNote {
  return {
    id,
    instrumentToken: 738561,
    notes,
    createdAt: "2026-07-29T10:00:00Z",
    updatedAt: "2026-07-29T10:00:00Z",
  };
}
