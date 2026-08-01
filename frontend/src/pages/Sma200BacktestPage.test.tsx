import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Sma200BacktestPage } from "./Sma200BacktestPage";

const runMock = vi.fn();

vi.mock("../hooks/useSma200Backtest", () => ({
  useSma200Backtest: () => ({ data: null, loading: false, error: null, run: runMock }),
}));

vi.mock("../hooks/useInstrumentSearch", () => ({
  useInstrumentSearch: () => ({ allInstruments: [], loading: false, error: null }),
}));

vi.mock("../components/InstrumentSearch", () => ({
  InstrumentSearch: ({ onSelect }: { onSelect: (instrument: { trading_symbol: string; instrument_token: number }) => void }) => (
    <button onClick={() => onSelect({ trading_symbol: "INFY", instrument_token: 123 })}>Select instrument</button>
  ),
}));

describe("Sma200BacktestPage", () => {
  it("runs the selected stock backtest", () => {
    render(<Sma200BacktestPage />);

    fireEvent.click(screen.getByRole("button", { name: "Select instrument" }));
    fireEvent.click(screen.getByRole("button", { name: /Run INFY Backtest/i }));

    expect(runMock).toHaveBeenCalledWith({ symbol: "INFY", instrumentToken: 123, entrySmaPeriod: 200 });
  });

  it("runs using the selected SMA period", async () => {
    render(<Sma200BacktestPage />);

    fireEvent.click(screen.getByRole("button", { name: "Select instrument" }));
    fireEvent.mouseDown(screen.getByLabelText("Entry SMA"));
    fireEvent.click(await screen.findByText("SMA50"));
    fireEvent.click(screen.getByRole("button", { name: /Run INFY Backtest/i }));

    expect(runMock).toHaveBeenLastCalledWith({ symbol: "INFY", instrumentToken: 123, entrySmaPeriod: 50 });
  });
});
