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
  it("runs NETWEB by default", () => {
    render(<WeeklyFloorReboundPage />);

    fireEvent.click(screen.getByRole("button", { name: /run netweb backtest/i }));

    expect(runMock).toHaveBeenCalledWith({ symbol: "NETWEB" });
  });

  it("runs the symbol selected from the instrument search", () => {
    selectedSymbol = "INFY";
    render(<WeeklyFloorReboundPage />);

    fireEvent.click(screen.getByRole("button", { name: /select instrument/i }));
    fireEvent.click(screen.getByRole("button", { name: /run infy backtest/i }));

    expect(runMock).toHaveBeenCalledWith({ symbol: "INFY" });
  });
});
