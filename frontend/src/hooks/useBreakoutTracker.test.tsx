import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useBreakoutTracker } from "./useBreakoutTracker";

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();
const deleteJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
  postJson: (...args: unknown[]) => postJsonMock(...args),
  deleteJson: (...args: unknown[]) => deleteJsonMock(...args),
}));

describe("useBreakoutTracker", () => {
  it("loads entries and keeps the list in sync after saving and removing an entry", async () => {
    const reliance = entry(1, "RELIANCE", "2026-07-20");
    const bel = entry(2, "BEL", "2026-07-25");
    getJsonMock.mockResolvedValue([reliance]);
    postJsonMock.mockResolvedValue(bel);

    const { result } = renderHook(() => useBreakoutTracker());
    await waitFor(() => expect(result.current.entries).toEqual([reliance]));

    await act(async () => {
      expect(await result.current.saveEntry({
        instrumentToken: bel.instrumentToken,
        symbol: bel.symbol,
        companyName: bel.companyName,
        breakoutDate: bel.breakoutDate,
        breakoutPrice: bel.breakoutPrice,
        notes: bel.notes,
      })).toBe(true);
    });

    expect(postJsonMock).toHaveBeenCalledWith("/api/breakout-tracker", expect.objectContaining({ symbol: "BEL" }));
    expect(result.current.entries.map((currentEntry) => currentEntry.symbol)).toEqual(["BEL", "RELIANCE"]);

    await act(async () => {
      expect(await result.current.removeEntry(2)).toBe(true);
    });

    expect(deleteJsonMock).toHaveBeenCalledWith("/api/breakout-tracker/2");
    expect(result.current.entries).toEqual([reliance]);
  });
});

function entry(id: number, symbol: string, breakoutDate: string) {
  return {
    id,
    instrumentToken: id * 100,
    symbol,
    companyName: `${symbol} Limited`,
    breakoutDate,
    breakoutPrice: 100,
    notes: "Volume and delivery evidence",
  };
}
