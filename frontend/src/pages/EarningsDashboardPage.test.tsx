import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { EarningsDashboardPage } from "./EarningsDashboardPage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("EarningsDashboardPage", () => {
  beforeEach(() => {
    getJsonMock.mockReset();
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        generated_at: "2026-04-22T10:00:00+05:30",
        filters: { daysAhead: 15, growwOnly: true },
        calculated_rows: [],
        raw_daily_candles_20d: [],
      }),
    }) as unknown as typeof fetch;
    Object.defineProperty(URL, "createObjectURL", {
      writable: true,
      value: vi.fn(() => "blob:mock"),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      writable: true,
      value: vi.fn(() => {}),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("loads and renders dashboard rows", async () => {
    getJsonMock.mockResolvedValue({
      asOfDate: "2026-04-22",
      daysAhead: 15,
      growwOnly: true,
      rows: [
        {
          symbol: "INFY",
          instrumentToken: 408065,
          resultDate: "2026-04-30",
          daysToResult: 8,
          isGrowwWatchlist: true,
          pre15dReturnPct: 6.2,
          pre10dReturnPct: 4.1,
          pre15dMaxDrawdownPct: -3.2,
          eventDayOcPct: null,
          eventDayOhPct: null,
          nextDayOcPct: null,
          nextDayOhPct: null,
          latestClose: 1540.1,
          latestVolume: 4523312,
          candleCoverage20d: 20,
        },
      ],
    });

    render(<EarningsDashboardPage />);

    await waitFor(() => {
      expect(screen.getByText("INFY")).toBeInTheDocument();
    });

    expect(getJsonMock).toHaveBeenCalledWith("/api/corporate-events/dashboard?daysAhead=15&growwOnly=true");
  });

  it("filters rows by symbol search", async () => {
    getJsonMock.mockResolvedValue({
      asOfDate: "2026-04-22",
      daysAhead: 15,
      growwOnly: true,
      rows: [
        {
          symbol: "INFY",
          instrumentToken: 408065,
          resultDate: "2026-04-30",
          daysToResult: 8,
          isGrowwWatchlist: true,
          pre15dReturnPct: null,
          pre10dReturnPct: null,
          pre15dMaxDrawdownPct: null,
          eventDayOcPct: null,
          eventDayOhPct: null,
          nextDayOcPct: null,
          nextDayOhPct: null,
          latestClose: null,
          latestVolume: null,
          candleCoverage20d: 11,
        },
        {
          symbol: "TCS",
          instrumentToken: 2953217,
          resultDate: "2026-04-29",
          daysToResult: 7,
          isGrowwWatchlist: true,
          pre15dReturnPct: null,
          pre10dReturnPct: null,
          pre15dMaxDrawdownPct: null,
          eventDayOcPct: null,
          eventDayOhPct: null,
          nextDayOcPct: null,
          nextDayOhPct: null,
          latestClose: null,
          latestVolume: null,
          candleCoverage20d: 12,
        },
      ],
    });

    render(<EarningsDashboardPage />);

    await waitFor(() => {
      expect(screen.getByText("INFY")).toBeInTheDocument();
      expect(screen.getByText("TCS")).toBeInTheDocument();
    });

    fireEvent.change(screen.getByPlaceholderText("Search symbol"), { target: { value: "INF" } });
    expect(screen.getByText("INFY")).toBeInTheDocument();
    expect(screen.queryByText("TCS")).not.toBeInTheDocument();
  });

  it("calls export endpoint when export button is clicked", async () => {
    getJsonMock.mockResolvedValue({
      asOfDate: "2026-04-22",
      daysAhead: 15,
      growwOnly: true,
      rows: [],
    });

    render(<EarningsDashboardPage />);

    await waitFor(() => {
      expect(getJsonMock).toHaveBeenCalledTimes(1);
    });

    const exportButton = screen.getByText("Export JSON").closest("button");
    expect(exportButton).not.toBeNull();
    fireEvent.click(exportButton!);

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith("/api/corporate-events/dashboard/export?daysAhead=15&growwOnly=true");
    });
  });
});
