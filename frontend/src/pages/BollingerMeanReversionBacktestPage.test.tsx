import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BollingerMeanReversionBacktestPage } from "./BollingerMeanReversionBacktestPage";

const useBollingerMeanReversionBacktestMock = vi.fn();

vi.mock("../hooks/useBollingerMeanReversionBacktest", () => ({
  useBollingerMeanReversionBacktest: () => useBollingerMeanReversionBacktestMock(),
}));

vi.mock("../utils/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../utils/api")>();
  return {
    ...actual,
    getJson: vi.fn().mockResolvedValue({
      options: [{ label: "Watchlist", value: "WATCHLIST", count: 20 }],
    }),
  };
});

describe("BollingerMeanReversionBacktestPage", () => {
  it("runs mean reversion backtest using UI config", () => {
    const run = vi.fn();
    useBollingerMeanReversionBacktestMock.mockReturnValue({ data: null, loading: false, error: null, run });

    render(<BollingerMeanReversionBacktestPage />);
    fireEvent.click(screen.getByRole("button", { name: /Run Bollinger Mean Reversion Backtest/i }));

    expect(run).toHaveBeenCalledTimes(1);
    expect(run.mock.calls[0][0].universe).toBe("WATCHLIST");
  });
});
