import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { WeeklyFloorReboundPage } from "./WeeklyFloorReboundPage";

const runMock = vi.fn();
let selectedSymbol = "NETWEB";

vi.mock("../hooks/useWeeklyFloorReboundBacktest", () => ({
  useWeeklyFloorReboundBacktest: () => ({ data: null, loading: false, error: null, run: runMock }),
}));

vi.mock("../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => ({ allInstruments: [], loading: false, error: null }),
}));

vi.mock("../components/InstrumentSearch", () => ({
  InstrumentSearch: ({ onSelect }: { onSelect: (instrument: { trading_symbol: string } | null) => void }) => (
    <button onClick={() => onSelect({ trading_symbol: selectedSymbol })}>Select instrument</button>
  ),
}));

describe("WeeklyFloorReboundPage", () => {
  function completeManualZone(): void {
    fireEvent.change(screen.getByPlaceholderText("Support floor"), { target: { value: "3015" } });
    fireEvent.change(screen.getByPlaceholderText("Support ceiling"), { target: { value: "3080" } });
    fireEvent.change(screen.getByPlaceholderText("Active from (YYYY-MM-DD)"), { target: { value: "2025-10-10" } });
  }

  it("runs NETWEB by default with the manual zone", () => {
    render(<WeeklyFloorReboundPage />);
    completeManualZone();

    fireEvent.click(screen.getByRole("button", { name: /run netweb backtest/i }));

    expect(runMock).toHaveBeenCalledWith({
      symbol: "NETWEB",
      supportFloor: 3015,
      supportCeiling: 3080,
      activeFrom: "2025-10-10",
    });
  });

  it("runs the symbol selected from the instrument search", () => {
    selectedSymbol = "INFY";
    render(<WeeklyFloorReboundPage />);

    fireEvent.click(screen.getByRole("button", { name: /select instrument/i }));
    completeManualZone();
    fireEvent.click(screen.getByRole("button", { name: /run infy backtest/i }));

    expect(runMock).toHaveBeenCalledWith(expect.objectContaining({ symbol: "INFY" }));
  });
});
