import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AdaptiveBreakoutScannerPage } from "./AdaptiveBreakoutScannerPage";

const getJsonMock = vi.fn();
const marketOpenMock = vi.fn(() => true);

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

vi.mock("../utils/marketHours", () => ({
  isIndianEquityMarketOpen: () => marketOpenMock(),
}));

function mockLatestScanApi(response: object = scanResponse): void {
  getJsonMock.mockImplementation((path: string) => {
    if (path === "/api/strategy/adaptive-breakout/watchlists") {
      return Promise.resolve({ options: [{ label: "leaders", value: "leaders", count: 1 }] });
    }
    if (path === "/api/strategy/adaptive-breakout/scan?watchlist=leaders") {
      return Promise.resolve(response);
    }
    if (path === "/api/stocks/quotes?symbols=ABC") {
      return Promise.resolve([{
        symbol: "ABC",
        ltp: 87,
        day_open: 85,
        day_high: 87,
        day_low: 84,
        volume: 1500,
        updated_at: "2026-08-15T06:00:00Z",
      }]);
    }
    return Promise.reject(new Error(`Unexpected path: ${path}`));
  });
}

describe("AdaptiveBreakoutScannerPage", () => {
  it("runs the latest watchlist scan and overlays LTP without changing completed-close evidence", async () => {
    marketOpenMock.mockReturnValue(true);
    mockLatestScanApi();

    render(<AdaptiveBreakoutScannerPage />);

    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("leaders (1)"));
    fireEvent.click(screen.getByRole("button", { name: /Run scan/i }));

    const table = await screen.findByTestId("adaptive-breakout-table");
    expect(within(table).getByText("ABC")).toBeInTheDocument();
    const kiteLink = within(table).getByRole("link", { name: "Open ABC in Kite" });
    expect(kiteLink).toHaveAttribute("href", "https://kite.zerodha.com/chart/web/tvc/NSE/ABC/123");
    expect(kiteLink).toHaveAttribute("target", "_blank");
    const detailLink = within(table).getByRole("link", { name: "Open ABC stock detail" });
    expect(detailLink).toHaveAttribute("href", "/TradingTool-3/console/compact-stock-review?symbol=ABC");
    expect(detailLink).toHaveAttribute("target", "_blank");
    expect(await within(table).findByText("Live candidate")).toBeInTheDocument();
    expect(within(table).getByText("₹87.00")).toBeInTheDocument();
    expect(within(table).getByText(/Last close ₹86\.00/)).toBeInTheDocument();
    expect(within(table).getByText(/Ceiling ₹86\.00/)).toBeInTheDocument();
    expect(within(table).getByText("1.40×")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Live candidate 1/i })).toBeInTheDocument();
    expect(screen.getByText(/completed close confirms breakout/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Fresh breakout 0" }));
    expect(await within(table).findByText("No stocks in this state.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "All stocks 1" }));
    expect(await within(table).findByText("ABC")).toBeInTheDocument();

    fireEvent.click(within(table).getByRole("button", { name: "Open ABC recent daily details" }));
    const detailsDialog = await screen.findByRole("dialog");
    expect(within(detailsDialog).getByText("ABC · Recent 20D details")).toBeInTheDocument();
    expect(within(detailsDialog).getByRole("columnheader", { name: "Volume vs 10D avg" })).toBeInTheDocument();
    expect(within(detailsDialog).getByRole("columnheader", { name: "Delivery %" })).toBeInTheDocument();
    expect(within(detailsDialog).getByRole("columnheader", { name: "Close position" })).toBeInTheDocument();
    expect(document.querySelector(".ant-modal-mask")).not.toBeInTheDocument();
    fireEvent.click(within(detailsDialog).getByRole("button", { name: "Close" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

    fireEvent.click(within(table).getByRole("button", { name: "Audit ABC" }));
    expect(document.querySelector(".ant-drawer-mask")).not.toBeInTheDocument();
    const auditDrawer = document.querySelector(".adaptive-breakout-audit-drawer-root") as HTMLElement;
    expect(auditDrawer).toBeInTheDocument();
    expect(await screen.findByText(/raw decision replay/i)).toBeInTheDocument();
    expect(within(auditDrawer).getByText("Read the story from the bottom ↑")).toBeInTheDocument();
    expect(within(auditDrawer).getByText("Floor found")).toBeInTheDocument();
    expect(within(auditDrawer).getByText("₹86.00 is now the confirmed line that a future close must beat.")).toBeInTheDocument();
    expect(within(auditDrawer).getAllByText("later obstacle")).toHaveLength(2);
    expect(auditDrawer.querySelector(".adaptive-breakout-raw-keys")).toHaveTextContent("Day shape = positions, not time order");
    const dayShape = within(auditDrawer).getByRole("img", {
      name: /Daily range: open ₹82\.00, high ₹83\.00, low ₹79\.00, close ₹80\.00, close below open\. Intraday high-low order is unknown\./,
    });
    expect(dayShape).toHaveClass("daily-ohlc-glyph-down");
    expect(within(auditDrawer).getByLabelText(
      "Down move ₹5.00, 2.50 ATR, required 0.75 ATR or ₹1.50, new down leg; candidate low starts ₹79.00.",
    )).toHaveTextContent("↓ 2.50 ATR≥ 0.75 ✓peak ₹85.00 − close ₹80.00 = ₹5.00 · candidate low → ₹79.00");
    expect(within(auditDrawer).getByLabelText(
      "Up move ₹4.00, 2.00 ATR, required 1.00 ATR or ₹2.00, floor confirmed.",
    )).toHaveTextContent("↑ 2.00 ATR≥ 1.00 ✓close ₹84.00 − floor ₹80.00 = ₹4.00 · floor confirmed");
    expect(await screen.findByText("CEILING CONFIRMED")).toBeInTheDocument();
    expect(screen.getByText("The rebound peaked and was rejected.")).toBeInTheDocument();
    await waitFor(() => expect(getJsonMock).toHaveBeenCalledWith("/api/stocks/quotes?symbols=ABC"));
  });

  it("treats the final Kite snapshot as the completed close after market hours", async () => {
    marketOpenMock.mockReturnValue(false);
    mockLatestScanApi();

    render(<AdaptiveBreakoutScannerPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("leaders (1)"));
    fireEvent.click(screen.getByRole("button", { name: /Run scan/i }));

    const table = await screen.findByTestId("adaptive-breakout-table");
    expect(await within(table).findByText("Fresh breakout")).toBeInTheDocument();
    expect(within(table).getByText("100%")).toBeInTheDocument();
    expect(within(table).getAllByText(/Market close · 15 Aug/)).toHaveLength(2);
    expect(screen.getByRole("button", { name: "Fresh breakout 1" })).toBeInTheDocument();
  });

  it("shows the original breakout candle evidence for an already-broken ceiling", async () => {
    marketOpenMock.mockReturnValue(true);
    mockLatestScanApi({
      ...scanResponse,
      rows: [{
        ...scanResponse.rows[0],
        status: "BREAKOUT_CONTINUATION",
        ceiling: { ...scanResponse.rows[0].ceiling, breakoutDate: "2026-08-05" },
        breakoutEvidence: {
          date: "2026-08-05",
          closePositionPct: 90,
          volumeVsTenDayAverage: 2.1,
          distanceFromFiftyTwoWeekHighPct: -5,
        },
        rawSteps: [],
      }],
    });

    render(<AdaptiveBreakoutScannerPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("leaders (1)"));
    fireEvent.click(screen.getByRole("button", { name: /Run scan/i }));

    const table = await screen.findByTestId("adaptive-breakout-table");
    expect(await within(table).findByText("Already broken")).toBeInTheDocument();
    expect(within(table).getByText("Breakout close · 05 Aug")).toBeInTheDocument();
    expect(within(table).getByText("90%")).toBeInTheDocument();
    expect(within(table).getByText("2.10×")).toBeInTheDocument();
    expect(within(table).queryByText(/Latest close/)).not.toBeInTheDocument();
  });

  it("shows a strong rebound separately while keeping distant resistance as context", async () => {
    marketOpenMock.mockReturnValue(true);
    mockLatestScanApi({
      ...scanResponse,
      rows: [{
        ...scanResponse.rows[0],
        status: "STRONG_REBOUND",
        ceiling: null,
        majorCeiling: {
          anchorDate: "2026-06-01",
          confirmedDate: "2026-06-05",
          anchorPrice: 100,
          upperBoundary: 101,
          atrAtAnchor: 2,
          breakoutDate: null,
        },
      }],
    });

    render(<AdaptiveBreakoutScannerPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("leaders (1)"));
    fireEvent.click(screen.getByRole("button", { name: /Run scan/i }));

    const table = await screen.findByTestId("adaptive-breakout-table");
    expect(await within(table).findByText("Strong rebound")).toBeInTheDocument();
    expect(within(table).getByText("Strong rebound · 2 ATR+")).toBeInTheDocument();
    expect(within(table).getByText("Major overhead ₹101.00")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Strong rebound 1" })).toBeInTheDocument();
  });
});

const scanResponse = {
  watchlistKey: "leaders",
  requestedAsOfDate: "2026-08-15",
  latestCandleDate: "2026-08-14",
  scannedStockCount: 1,
  freshBreakoutCount: 0,
  config: {
    atrPeriod: 14,
    floorReboundAtrMultiple: 1,
    peakRejectionAtrMultiple: 0.75,
    ceilingWidthAtrMultiple: 0.5,
    maximumLocalCeilingDistanceAtrMultiple: 3,
    strongReboundAtrMultiple: 2,
  },
  rows: [{
    symbol: "ABC",
    companyName: "ABC Limited",
    instrumentToken: 123,
    status: "TESTING_CEILING",
    latestDate: "2026-08-14",
    latestOpen: 85,
    latestHigh: 86.5,
    latestLow: 84,
    latestClose: 86,
    latestVolume: 1400,
    latestAtr: 2,
    ceiling: {
      anchorDate: "2026-08-01",
      confirmedDate: "2026-08-05",
      anchorPrice: 85,
      upperBoundary: 86,
      atrAtAnchor: 2,
      breakoutDate: null,
    },
    majorCeiling: null,
    ceilingAgeSessions: 9,
    closeVsCeilingPct: 0,
    closePositionPct: 80,
    volumeVsTenDayAverage: 1.4,
    fiftyTwoWeekHigh: 100,
    distanceFromFiftyTwoWeekHighPct: -14,
    breakoutEvidence: null,
    rawSteps: [
      {
        date: "2026-08-03",
        open: 80,
        high: 85,
        low: 79,
        close: 80,
        volume: 900,
        atr: 2,
        candidateFloor: 79,
        candidatePeak: 85,
        ceilingAnchor: null,
        ceilingUpperBoundary: null,
        majorCeilingUpperBoundary: null,
        decision: "CEILING_CANDIDATE",
        explanation: "Price rejected ₹85.00 by at least 0.75 ATR.",
      },
      {
        date: "2026-08-04",
        open: 81,
        high: 85,
        low: 80,
        close: 84,
        volume: 950,
        atr: 2,
        candidateFloor: 80,
        candidatePeak: 85,
        ceilingAnchor: null,
        ceilingUpperBoundary: null,
        majorCeilingUpperBoundary: null,
        decision: "FLOOR_CONFIRMED",
        explanation: "Price moved one ATR above the candidate floor.",
      },
      {
        date: "2026-08-05",
        open: 82,
        high: 83,
        low: 79,
        close: 80,
        volume: 1000,
        atr: 2,
        candidateFloor: 80,
        candidatePeak: 85,
        ceilingAnchor: 85,
        ceilingUpperBoundary: 86,
        majorCeilingUpperBoundary: null,
        decision: "CEILING_CONFIRMED",
        explanation: "The rebound peaked and was rejected.",
      },
    ],
  }],
};
