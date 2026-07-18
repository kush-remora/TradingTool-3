import { render, screen, waitFor } from "@testing-library/react";
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
      rows: [{
        symbol: "BHEL",
        universeKey: "nifty_midcap_150",
        curatedWatchlists: ["growth_watchlist"],
        accumulationLatestDate: "2026-07-17",
        phaseDLatestDate: "2026-07-18",
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
  });
});
