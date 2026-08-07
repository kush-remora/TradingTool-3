import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { VolumeEventConfirmationBacktestPage } from "./VolumeEventConfirmationBacktestPage";

const useBacktestMock = vi.fn();
const getJsonMock = vi.fn();

vi.mock("../hooks/useVolumeEventConfirmationBacktest", () => ({
  useVolumeEventConfirmationBacktest: () => useBacktestMock(),
}));

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("VolumeEventConfirmationBacktestPage", () => {
  it("runs the selected-stock mode with the chosen watchlist and symbol", async () => {
    const run = vi.fn();
    useBacktestMock.mockReturnValue({ data: null, loading: false, error: null, run });
    getJsonMock.mockResolvedValue({ options: [{ value: "watchlist", label: "Watchlist", count: 3 }] });

    render(<VolumeEventConfirmationBacktestPage />);

    await waitFor(() => expect(screen.getByText("Volume Event Confirmation Backtest")).toBeInTheDocument());
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("Watchlist (3)"));
    fireEvent.change(screen.getByRole("textbox", { name: "Stock symbol" }), { target: { value: "BHEL" } });
    fireEvent.click(screen.getByRole("button", { name: "Run backtest" }));

    expect(run).toHaveBeenCalledWith({ watchlistKey: "watchlist", symbol: "BHEL" });
  });

  it("describes the fixed confirmation rules before a run", async () => {
    useBacktestMock.mockReturnValue({ data: null, loading: false, error: null, run: vi.fn() });
    getJsonMock.mockResolvedValue({ options: [] });

    render(<VolumeEventConfirmationBacktestPage />);

    expect(await screen.findByText(/volume ≥ 2× prior 5-session average/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Run backtest" })).toBeDisabled();
  });
});
