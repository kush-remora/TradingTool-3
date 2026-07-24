import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WeeklyBaseDefinitionPage } from "./WeeklyBaseDefinitionPage";

const runMock = vi.fn();
let mockData = null as ReturnType<typeof baseReport> | null;

vi.mock("../hooks/useWeeklyBaseDefinition", () => ({
  useWeeklyBaseDefinition: () => ({ data: mockData, loading: false, error: null, run: runMock }),
}));

vi.mock("../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => ({ allInstruments: [], loading: false, error: null }),
}));

vi.mock("../components/InstrumentSearch", () => ({
  InstrumentSearch: ({ onSelect }: { onSelect: (instrument: { trading_symbol: string } | null) => void }) => (
    <button onClick={() => onSelect({ trading_symbol: "INFY" })}>Select instrument</button>
  ),
}));

describe("WeeklyBaseDefinitionPage", () => {
  it("runs NETWEB by default", () => {
    render(<WeeklyBaseDefinitionPage />);

    fireEvent.click(screen.getByRole("button", { name: /find netweb bases/i }));

    expect(runMock).toHaveBeenCalledWith({ symbol: "NETWEB" });
  });

  it("shows a separate table containing only valid bases", () => {
    mockData = baseReport();
    render(<WeeklyBaseDefinitionPage />);

    expect(screen.getByText("Valid bases (1)")).toBeInTheDocument();
    expect(screen.getAllByText("2025-01-27")).toHaveLength(2);
    mockData = null;
  });

  it("runs the selected NSE equity", () => {
    render(<WeeklyBaseDefinitionPage />);

    fireEvent.click(screen.getByRole("button", { name: /select instrument/i }));
    fireEvent.click(screen.getByRole("button", { name: /find infy bases/i }));

    expect(runMock).toHaveBeenCalledWith({ symbol: "INFY" });
  });
});

function baseReport() {
  return {
    symbol: "NETWEB",
    testedFromDate: "2025-01-01",
    testedToDate: "2025-01-27",
    validBaseCount: 1,
    rows: [{
      evaluationDate: "2025-01-27",
      firstWeekStartDate: "2025-01-06",
      firstWeekLow: 100,
      secondWeekStartDate: "2025-01-13",
      secondWeekLow: 101,
      thirdWeekStartDate: "2025-01-20",
      thirdWeekLow: 102,
      zoneFloor: 100,
      zoneCeiling: 102,
      zoneWidthPct: 2,
      sma200: 110,
      distanceFromSma200Pct: -9.09,
      isWithinSma200Range: true,
      isValid: true,
      validityReason: "VALID",
    }],
  };
}
