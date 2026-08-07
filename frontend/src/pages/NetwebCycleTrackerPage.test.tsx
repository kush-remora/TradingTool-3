import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { NetwebCycleTrackerPage } from "./NetwebCycleTrackerPage";

const runMock = vi.fn();

vi.mock("../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => ({ loading: false, error: null }),
}));

vi.mock("../hooks/useNetwebCycle", () => ({
  useNetwebCycle: () => ({ data: report(), loading: false, error: null, run: runMock }),
}));

describe("NetwebCycleTrackerPage", () => {
  it("refreshes the fixed NETWEB cycle report", () => {
    render(<NetwebCycleTrackerPage />);

    fireEvent.click(screen.getByRole("button", { name: /refresh netweb/i }));

    expect(runMock).toHaveBeenCalledWith({ symbol: "NETWEB" });
    expect(screen.getByText("Bull run")).toBeInTheDocument();
    expect(screen.getByText(/ride the expansion/i)).toBeInTheDocument();
  });
});

function report() {
  return {
    symbol: "NETWEB",
    testedFromDate: "2026-07-01",
    testedToDate: "2026-08-07",
    current: {
      date: "2026-08-07",
      phase: "BULL_RUN" as const,
      currentPrice: 4939.2,
      baseLow: 4156,
      baseHigh: 4400,
      baseWidthPct: 5.87,
      positionInBasePct: null,
      dailyChangePct: 2.56,
      fiveDayReturnPct: 9.2,
      twentyDayReturnPct: 18.7,
      volumeRatio20Day: 1.3,
      expansionPeak: 4968,
      drawdownFromPeakPct: -0.58,
      phaseStartDate: "2026-07-31",
      phaseAgeTradingDays: 6,
      fivePercentMoveCount: 0,
      breakoutAboveBase: true,
      confidencePct: 80,
      action: "Ride the expansion; monitor for a controlled pullback.",
      evidence: ["Price broke above the active base with breakout follow-through."],
    },
    segments: [],
    dailySnapshots: [],
  };
}
