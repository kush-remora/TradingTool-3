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
  it("shows one volume-shocker rule without RSI controls", async () => {
    useBacktestMock.mockReturnValue({ data: null, loading: false, error: null, run: vi.fn() });
    getJsonMock.mockResolvedValue({ options: [] });

    render(<VolumeEventConfirmationBacktestPage />);

    expect(await screen.findByText(/today must be a new volume shocker/)).toBeInTheDocument();
    expect(screen.queryByText(/RSI/)).not.toBeInTheDocument();
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
  });

  it("runs the six-month backtest for selected watchlists and target", async () => {
    const run = vi.fn();
    useBacktestMock.mockReturnValue({ data: null, loading: false, error: null, run });
    getJsonMock.mockResolvedValue({ options: [{ value: "watchlist", label: "Watchlist", count: 3 }] });

    render(<VolumeEventConfirmationBacktestPage />);

    await waitFor(() => expect(screen.getByText("Volume Event Confirmation Backtest")).toBeInTheDocument());
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlists" }));
    fireEvent.click(await screen.findByText("Watchlist (3)"));
    fireEvent.click(screen.getByRole("button", { name: "Run backtest" }));

    expect(run).toHaveBeenCalledWith({
      watchlists: ["watchlist"],
      targetPct: 10,
    });
  });
});
