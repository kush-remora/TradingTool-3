import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AdaptiveBreakoutBacktestPage } from "./AdaptiveBreakoutBacktestPage";

const runMock = vi.fn();

vi.mock("../hooks/useAdaptiveBreakoutBacktest", () => ({
  useAdaptiveBreakoutBacktest: () => ({ data: null, loading: false, error: null, run: runMock }),
}));

vi.mock("../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => ({ allInstruments: [], loading: false, error: null }),
}));

vi.mock("../components/InstrumentSearch", () => ({
  InstrumentSearch: ({ onSelect }: { onSelect: (instrument: { trading_symbol: string; instrument_token: number }) => void }) => (
    <button onClick={() => onSelect({ trading_symbol: "INFY", instrument_token: 123 })}>Select instrument</button>
  ),
}));

describe("AdaptiveBreakoutBacktestPage", () => {
  it("runs the fixed five-percent test for the selected stock", () => {
    render(<AdaptiveBreakoutBacktestPage />);

    fireEvent.click(screen.getByRole("button", { name: "Select instrument" }));
    fireEvent.click(screen.getByRole("button", { name: /Run INFY test/i }));

    expect(runMock).toHaveBeenCalledWith({
      symbol: "INFY",
      instrumentToken: 123,
      months: 6,
      targetPct: 5,
      stopLossPct: 5,
    });
  });
});
