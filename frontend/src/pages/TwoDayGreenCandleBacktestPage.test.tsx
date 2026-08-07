import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TwoDayGreenCandleBacktestPage } from "./TwoDayGreenCandleBacktestPage";

const useBacktestMock = vi.fn();
vi.mock("../hooks/useTwoDayGreenCandleBacktest", () => ({
  useTwoDayGreenCandleBacktest: () => useBacktestMock(),
}));

vi.mock("../utils/api", () => ({
  getJson: vi.fn().mockResolvedValue({ options: [{ value: "nifty_50", label: "NIFTY 50", count: 50 }] }),
}));

describe("TwoDayGreenCandleBacktestPage", () => {
  it("renders the strategy definition and run control", async () => {
    useBacktestMock.mockReturnValue({ data: null, loading: false, error: null, run: vi.fn() });

    render(<TwoDayGreenCandleBacktestPage />);

    await waitFor(() => {
      expect(screen.getByText("Two-Day Green Candle Backtest")).toBeInTheDocument();
      expect(screen.getByText(/two prior days closed above their opens/)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "Run backtest" })).toBeDisabled();
    });
  });
});
