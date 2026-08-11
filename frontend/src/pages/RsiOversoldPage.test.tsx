import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { RsiOversoldPage } from "./RsiOversoldPage";

const runMock = vi.fn();
const getJsonMock = vi.fn();
const useRsiOversoldScannerMock = vi.fn();

vi.mock("../hooks/useRsiOversoldScanner", () => ({
  useRsiOversoldScanner: () => useRsiOversoldScannerMock(),
}));

vi.mock("../hooks/useStockQuotes", () => ({
  useStockQuotes: () => ({ quotesBySymbol: {}, loading: false, error: null }),
}));

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("RsiOversoldPage", () => {
  it("does not show an error when stocks were evaluated without a qualifying signal", async () => {
    getJsonMock.mockResolvedValue({ options: [{ label: "WATCHLIST", value: "WATCHLIST", count: 1 }] });
    useRsiOversoldScannerMock.mockReturnValue({
      data: {
        selectedIndexKeys: ["WATCHLIST"],
        config: { rsiPeriod: 14, baselineSessions: 200, signalWindowSessions: 20, signalOffset: 1, asOfDate: "2026-08-10" },
        scannedStockCount: 1,
        resultCount: 0,
        insufficientDataSymbols: [],
        noSignalSymbols: ["INFY"],
        rows: [],
      },
      loading: false,
      error: null,
      run: runMock,
    });

    render(<RsiOversoldPage />);

    expect(await screen.findByText("No qualifying RSI signals were found.")).toBeInTheDocument();
    expect(screen.queryByText(/could not be evaluated/)).not.toBeInTheDocument();
  });

  it("renders the RSI-low candle and current market fields", async () => {
    getJsonMock.mockResolvedValue({ options: [{ label: "WATCHLIST", value: "WATCHLIST", count: 1 }] });
    useRsiOversoldScannerMock.mockReturnValue({
      data: {
        selectedIndexKeys: ["WATCHLIST"],
        config: { rsiPeriod: 14, baselineSessions: 200, signalWindowSessions: 20, signalOffset: 1, asOfDate: "2026-08-10" },
        scannedStockCount: 1,
        resultCount: 1,
        insufficientDataSymbols: [],
        noSignalSymbols: [],
        rows: [{
          symbol: "INFY",
          companyName: "Infosys",
          watchlistKeys: ["WATCHLIST"],
          signalDate: "2026-08-05",
          signalRsi: 19,
          signalPrice: 1_500,
          signalVolume: 10_000,
          baselineRsiLow: 20,
          latestDate: "2026-08-10",
          latestClose: 1_550,
          latestVolume: 12_000,
        }],
      },
      loading: false,
      error: null,
      run: runMock,
    });

    render(<RsiOversoldPage />);

    expect(await screen.findByText("INFY")).toBeInTheDocument();
    expect(screen.getByText("19")).toBeInTheDocument();
    expect(screen.getByText("₹1,500.00")).toBeInTheDocument();
    expect(screen.getByText("₹1,550.00")).toBeInTheDocument();
    expect(screen.getByText("+3.33%")).toBeInTheDocument();
  });

  it("loads watchlists and runs with multiple selections", async () => {
    useRsiOversoldScannerMock.mockReturnValue({ data: null, loading: false, error: null, run: runMock });
    getJsonMock.mockResolvedValue({
      options: [
        { label: "WATCHLIST", value: "WATCHLIST", count: 1 },
        { label: "NIFTY 50", value: "NIFTY 50", count: 50 },
      ],
    });

    render(<RsiOversoldPage />);

    const select = await screen.findByRole("combobox", { name: "Watchlists" });
    fireEvent.mouseDown(select);
    fireEvent.click(await screen.findByText("NIFTY 50 (50)"));
    fireEvent.click(screen.getByRole("button", { name: "Run Scanner" }));

    await waitFor(() => expect(runMock).toHaveBeenCalledWith({ indexKeys: ["WATCHLIST", "NIFTY 50"] }));
  });
});
