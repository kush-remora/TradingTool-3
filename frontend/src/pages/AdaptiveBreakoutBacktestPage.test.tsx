import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AdaptiveBreakoutBacktestPage } from "./AdaptiveBreakoutBacktestPage";

const runMock = vi.fn();
const getJsonMock = vi.hoisted(() => vi.fn());

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

vi.mock("../utils/api", () => ({ getJson: getJsonMock }));

describe("AdaptiveBreakoutBacktestPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("runs the selected stock with the configured target and stop loss", () => {
    render(<AdaptiveBreakoutBacktestPage />);

    fireEvent.click(screen.getByRole("button", { name: "Select instrument" }));
    fireEvent.change(screen.getByRole("spinbutton", { name: "Target percentage" }), { target: { value: "8" } });
    fireEvent.change(screen.getByRole("spinbutton", { name: "Stop loss percentage" }), { target: { value: "3" } });
    fireEvent.click(screen.getByRole("button", { name: /Run INFY test/i }));

    expect(runMock).toHaveBeenCalledWith({
      symbol: "INFY",
      instrumentToken: 123,
      months: 6,
      targetPct: 8,
      stopLossPct: 3,
    });
  });

  it("runs the selected watchlist with the configured target and stop loss", async () => {
    getJsonMock.mockResolvedValue({ options: [{ value: "leaders", label: "leaders", count: 2 }] });
    render(<AdaptiveBreakoutBacktestPage />);

    fireEvent.click(screen.getByRole("radio", { name: "Watchlist" }));
    fireEvent.mouseDown(await screen.findByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("leaders (2)"));
    fireEvent.change(screen.getByRole("spinbutton", { name: "Target percentage" }), { target: { value: "12" } });
    fireEvent.change(screen.getByRole("spinbutton", { name: "Stop loss percentage" }), { target: { value: "4" } });
    fireEvent.click(screen.getByRole("button", { name: /Run leaders test/i }));

    expect(runMock).toHaveBeenCalledWith({
      watchlistKey: "leaders",
      months: 6,
      targetPct: 12,
      stopLossPct: 4,
    });
  });
});
