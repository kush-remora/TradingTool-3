import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ChartinkEvidencePage } from "./ChartinkEvidencePage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
  postJson: vi.fn(),
}));

describe("ChartinkEvidencePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getJsonMock.mockResolvedValue({
      months: 1,
      fromDate: "2026-06-18",
      uploadStatuses: [{
        slot: "ACCUMULATION_NIFTY_MIDCAP_150",
        sourceFileName: "midcap.csv",
        uploadedAt: "2026-07-18T12:00:00Z",
      }],
      rows: [{
        symbol: "BHEL",
        universeKey: "nifty_midcap_150",
        curatedWatchlists: ["growth_watchlist"],
        accumulationLatestDate: "2026-07-17",
        phaseDLatestDate: "2026-07-18",
        t2HighLatestDate: null,
        freshBreakoutLatestDate: null,
      }, {
        symbol: "INFY",
        universeKey: "nifty_100",
        curatedWatchlists: [],
        accumulationLatestDate: "2026-07-17",
        phaseDLatestDate: null,
        t2HighLatestDate: null,
        freshBreakoutLatestDate: null,
      }],
    });
  });

  it("renders seven fixed upload slots and the evidence row", async () => {
    render(<ChartinkEvidencePage />);

    await waitFor(() => {
      expect(getJsonMock).toHaveBeenCalledWith(
        "/api/strategy/chartink-evidence/dashboard?months=1",
        { useCache: false },
      );
    });

    expect(screen.getAllByRole("button", { name: /Upload CSV/ })).toHaveLength(7);
    expect(screen.getByText("BHEL")).toBeInTheDocument();
    expect(screen.getByText("growth_watchlist")).toBeInTheDocument();
    expect(screen.getByText("2026-07-18")).toBeInTheDocument();
    expect(screen.getByText(/midcap\.csv/)).toBeInTheDocument();
    expect(screen.getByText("Nifty 100")).toBeInTheDocument();
    expect(screen.getByText("Midcap 150")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Nifty 100"));

    expect(screen.getByText("INFY")).toBeInTheDocument();
    expect(screen.queryByText("BHEL")).not.toBeInTheDocument();
  });
});
