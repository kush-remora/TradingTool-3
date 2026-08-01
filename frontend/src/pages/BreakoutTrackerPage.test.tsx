import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BreakoutTrackerPage } from "./BreakoutTrackerPage";

const useBreakoutTrackerMock = vi.fn();
const useInstrumentSearchMock = vi.fn();
const useStockQuotesMock = vi.fn();
const createObjectUrlMock = vi.fn(() => "blob:breakout-tracker");

vi.mock("../hooks/useBreakoutTracker", () => ({
  useBreakoutTracker: () => useBreakoutTrackerMock(),
}));

vi.mock("../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => useInstrumentSearchMock(),
}));

vi.mock("../hooks/useStockQuotes", () => ({
  useStockQuotes: (...args: unknown[]) => useStockQuotesMock(...args),
}));

describe("BreakoutTrackerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useBreakoutTrackerMock.mockReturnValue({
      entries: [
        {
          id: 1,
          instrumentToken: 101,
          symbol: "INFY",
          companyName: "Infosys Limited",
          breakoutDate: "2026-07-25",
          breakoutPrice: 100,
          notes: "Volume evidence",
        },
      ],
      loading: false,
      error: null,
      saveEntry: vi.fn(),
      removeEntry: vi.fn(),
    });
    useInstrumentSearchMock.mockReturnValue({ allInstruments: [], loading: false, error: null });
    useStockQuotesMock.mockReturnValue({ quotesBySymbol: {}, loading: false, error: null });
    Object.defineProperty(URL, "createObjectURL", { configurable: true, writable: true, value: createObjectUrlMock });
    Object.defineProperty(URL, "revokeObjectURL", { configurable: true, writable: true, value: vi.fn() });
  });

  it("downloads all tracked entries as a CSV file", () => {
    const clickMock = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});

    render(<BreakoutTrackerPage onOpenStockReview={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Download all entries as CSV" }));

    expect(createObjectUrlMock).toHaveBeenCalledOnce();
    expect(clickMock).toHaveBeenCalledOnce();
    expect(clickMock.mock.instances[0]).toHaveProperty("download", expect.stringMatching(/^breakout_tracker_\d{4}-\d{2}-\d{2}\.csv$/));
  }, 15000);
});
