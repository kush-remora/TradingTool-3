import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { WeeklyCycleSuccessPage } from "./WeeklyCycleSuccessPage";

const getJsonMock = vi.fn();
const clearCacheMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
  clearCache: (...args: unknown[]) => clearCacheMock(...args),
}));

vi.mock("../components/StockBadge", () => ({
  StockBadge: ({ symbol }: { symbol: string }) => <span>{symbol}</span>,
}));

describe("WeeklyCycleSuccessPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads with default params and renders rows", async () => {
    getJsonMock.mockResolvedValue({
      runAt: "2026-04-19T00:00:00Z",
      universe: "BOTH",
      weeksRequested: 8,
      weeksEvaluated: 8,
      highLowThresholdPct: 10,
      rocThresholdPct: 2,
      stableBaseMaxDriftPct: 4,
      results: [
        {
          symbol: "ABC",
          companyName: "ABC LTD",
          instrumentToken: 1,
          universeBuckets: ["NIFTY MIDCAP 250"],
          successCount: 6,
          cycleCount: 8,
          successRatePct: 75,
          failedStartWeeks: [],
          lastCycleMetrics: {
            weekLabel: "2026-W15",
            startDay: "Mon",
            endDay: "Fri",
            startLow: 100,
            endClose: 104,
            weekHigh: 112,
            highLowPct: 12,
            rocPct: 4,
            success: true,
          },
          stableBasePass: true,
          stableBaseReason: null,
          stableBaseDriftPct: 2.5,
          stableBaseLowMin: 100,
          stableBaseLowMax: 102.5,
          stableBaseWeeksCount: 4,
        },
      ],
    });

    render(<WeeklyCycleSuccessPage />);

    await waitFor(() => {
      expect(getJsonMock).toHaveBeenCalledWith(
        "/api/screener/weekly-cycle-success?universe=BOTH&weeks=8&highLowPct=10&rocPct=2&stableBaseDriftPct=4",
        { useCache: true },
      );
    });

    expect(screen.getByText("ABC")).toBeInTheDocument();
    expect(screen.getByText("6/8")).toBeInTheDocument();
    expect(screen.getByText("75.00%")).toBeInTheDocument();
  });

  it("runs scan with updated numeric params", async () => {
    getJsonMock
      .mockResolvedValueOnce({
        runAt: "2026-04-19T00:00:00Z",
        universe: "BOTH",
        weeksRequested: 8,
        weeksEvaluated: 8,
        highLowThresholdPct: 10,
        rocThresholdPct: 2,
        stableBaseMaxDriftPct: 4,
        results: [],
      })
      .mockResolvedValue({
      runAt: "2026-04-19T00:00:00Z",
      universe: "BOTH",
      weeksRequested: 10,
      weeksEvaluated: 10,
      highLowThresholdPct: 9,
      rocThresholdPct: 3,
      stableBaseMaxDriftPct: 5,
      results: [],
    });

    render(<WeeklyCycleSuccessPage />);

    await waitFor(() => expect(getJsonMock).toHaveBeenCalledTimes(1));

    const weeksInput = screen.getByTestId("weeks-input");
    const highLowInput = screen.getByTestId("high-low-input");
    const rocInput = screen.getByTestId("roc-input");
    const stableBaseDriftInput = screen.getByTestId("stable-base-drift-input");

    fireEvent.change(weeksInput, { target: { value: "10" } });
    fireEvent.blur(weeksInput);
    fireEvent.change(highLowInput, { target: { value: "9" } });
    fireEvent.blur(highLowInput);
    fireEvent.change(rocInput, { target: { value: "3" } });
    fireEvent.blur(rocInput);
    fireEvent.change(stableBaseDriftInput, { target: { value: "5" } });
    fireEvent.blur(stableBaseDriftInput);

    await waitFor(() => {
      expect(getJsonMock).toHaveBeenLastCalledWith(
        "/api/screener/weekly-cycle-success?universe=BOTH&weeks=10&highLowPct=9&rocPct=3&stableBaseDriftPct=5",
        { useCache: true },
      );
    });
  });

  it("shows error alert when fetch fails", async () => {
    getJsonMock.mockRejectedValue(new Error("boom"));

    render(<WeeklyCycleSuccessPage />);

    expect(await screen.findByText("Scan failed")).toBeInTheDocument();
    expect(screen.getAllByText("boom").length).toBeGreaterThan(0);
  });

  it("toggles between execution and full column views", async () => {
    getJsonMock.mockResolvedValue({
      runAt: "2026-04-19T00:00:00Z",
      universe: "BOTH",
      weeksRequested: 8,
      weeksEvaluated: 8,
      highLowThresholdPct: 10,
      rocThresholdPct: 2,
      stableBaseMaxDriftPct: 4,
      results: [
        {
          symbol: "ABC",
          companyName: "ABC LTD",
          instrumentToken: 1,
          universeBuckets: ["NIFTY MIDCAP 250"],
          successCount: 6,
          cycleCount: 8,
          successRatePct: 75,
          failedStartWeeks: ["2026-W10"],
          lastCycleMetrics: {
            weekLabel: "2026-W15",
            startDay: "Mon",
            endDay: "Fri",
            startLow: 100,
            endClose: 104,
            weekHigh: 112,
            highLowPct: 12,
            rocPct: 4,
            success: true,
          },
          stableBasePass: true,
          stableBaseReason: null,
          stableBaseDriftPct: 2.5,
          stableBaseLowMin: 100,
          stableBaseLowMax: 102.5,
          stableBaseWeeksCount: 4,
        },
      ],
    });

    render(<WeeklyCycleSuccessPage />);

    expect((await screen.findAllByText("Success (x/y | %)")).length).toBeGreaterThan(0);
    expect(screen.queryByText("Failed Start Weeks")).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("execution-mode-switch"));

    expect((await screen.findAllByText("Failed Start Weeks")).length).toBeGreaterThan(0);
  });
});
