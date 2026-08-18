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

vi.mock("../hooks/useStockDetail", () => ({
  useStockDetail: () => ({
    data: {
      delivery_days: [{ date: "2026-08-05", delivery_percentage: 42.5 }],
    },
    loading: false,
    error: null,
  }),
}));

function mockLatestScanApi(response: object = scanResponse): void {
  getJsonMock.mockImplementation((path: string) => {
    if (path === "/api/strategy/adaptive-breakout/watchlists") {
      return Promise.resolve({ options: [{ label: "leaders", value: "leaders", count: 1 }] });
    }
    if (path === "/api/strategy/adaptive-breakout/scan?watchlist=leaders") {
      return Promise.resolve(response);
    }
    if (path === "/api/strategy/adaptive-breakout/scan?symbol=ABC") {
      return Promise.resolve(response);
    }
    if (path === "/api/stocks/instruments") {
      return Promise.resolve([{
        instrument_token: 123,
        trading_symbol: "ABC",
        company_name: "ABC Limited",
        exchange: "NSE",
        instrument_type: "EQ",
      }]);
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
  it("runs the same scanner for one selected NSE stock", async () => {
    marketOpenMock.mockReturnValue(true);
    mockLatestScanApi();

    render(<AdaptiveBreakoutScannerPage />);
    fireEvent.click(screen.getByRole("radio", { name: "Stock" }));
    const stockSearch = await screen.findByRole("combobox");
    fireEvent.change(stockSearch, { target: { value: "ABC" } });
    const matches = await screen.findAllByText("ABC - ABC Limited");
    fireEvent.click(matches[matches.length - 1]);
    fireEvent.click(await screen.findByRole("button", { name: /Run ABC/ }));

    const table = await screen.findByTestId("adaptive-breakout-table");
    expect(await within(table).findByText("ABC")).toBeInTheDocument();
    expect(getJsonMock).toHaveBeenCalledWith(
      "/api/strategy/adaptive-breakout/scan?symbol=ABC",
      { useCache: false },
    );
  });

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
    const buyReviewLink = within(table).getByRole("link", { name: "Review ABC breakout day quality" });
    expect(buyReviewLink).toHaveAttribute("href", "/TradingTool-3/console/breakout-buy-review?symbol=ABC&date=2026-08-14");
    expect(buyReviewLink).toHaveAttribute("target", "_blank");
    expect(await within(table).findByText("Live candidate")).toBeInTheDocument();
    expect(within(table).getByText("₹87.00")).toBeInTheDocument();
    expect(within(table).getByText(/Last close ₹86\.00/)).toBeInTheDocument();
    expect(within(table).getByText(/Ceiling ₹86\.00/)).toBeInTheDocument();
    expect(within(table).getByText("Strong rejection · 9 sessions · 2 tests · confirmed 05 Aug")).toBeInTheDocument();
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
    expect(within(detailsDialog).getByText("42.5%")).toBeInTheDocument();
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
    expect(within(auditDrawer).getByText("₹86.00 is now the active strong-rejection line to beat.")).toBeInTheDocument();
    expect(within(auditDrawer).getAllByText("later obstacle")).toHaveLength(2);
    expect(auditDrawer.querySelector(".adaptive-breakout-raw-keys")).toHaveTextContent("Day shape = positions, not time order");
    const dayShape = within(auditDrawer).getByRole("img", {
      name: /Daily range: open ₹82\.00, high ₹83\.00, low ₹79\.00, close ₹80\.00, close below open\. Intraday high-low order is unknown\./,
    });
    expect(dayShape).toHaveClass("daily-ohlc-glyph-down");
    expect(within(auditDrawer).getByLabelText(
      "Down move ₹5.00, 2.50 ATR, required 1.00 ATR or ₹2.00, ceiling confirmed; new down leg starts at ₹79.00.",
    )).toHaveTextContent("↓ 2.50 ATR≥ 1.00 ✓peak ₹85.00 − close ₹80.00 = ₹5.00 · ceiling confirmed · down leg → ₹79.00");
    expect(within(auditDrawer).getByLabelText(
      "Up move ₹4.00, 2.00 ATR, required 1.00 ATR or ₹2.00, floor confirmed.",
    )).toHaveTextContent("↑ 2.00 ATR≥ 1.00 ✓close ₹84.00 − floor ₹80.00 = ₹4.00 · floor confirmed");
    expect(await screen.findByText("CEILING CONFIRMED")).toBeInTheDocument();
    expect(screen.getByText("Price rejected ₹85.00 by at least 1.00 ATR.")).toBeInTheDocument();
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

  it("shows the original ceiling and failed-attempt cap as separate levels", async () => {
    marketOpenMock.mockReturnValue(true);
    const latestStep = scanResponse.rows[0].rawSteps.at(-1)!;
    mockLatestScanApi({
      ...scanResponse,
      rows: [{
        ...scanResponse.rows[0],
        latestClose: 88,
        ceiling: {
          ...scanResponse.rows[0].ceiling,
          baseUpperBoundary: 86,
          upperBoundary: 89,
          failedAttemptHigh: 89,
          lastFailedAttemptDate: "2026-08-13",
        },
        rawSteps: [{
          ...latestStep,
          close: 88,
          ceilingBaseUpperBoundary: 86,
          ceilingUpperBoundary: 89,
          ceilingFailedAttemptHigh: 89,
          decision: "CEILING_RECLAIM",
          explanation: "The close reclaimed the original line; the strict cap remains.",
        }],
      }],
    });

    render(<AdaptiveBreakoutScannerPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("leaders (1)"));
    fireEvent.click(screen.getByRole("button", { name: /Run scan/i }));

    const table = await screen.findByTestId("adaptive-breakout-table");
    expect(within(table).getByText("Strict cap ₹89.00")).toBeInTheDocument();
    expect(within(table).getByText(/base ₹86\.00 reclaimed first/)).toBeInTheDocument();
    fireEvent.click(within(table).getByRole("button", { name: "Audit ABC" }));
    const auditDrawer = document.querySelector(".adaptive-breakout-audit-drawer-root") as HTMLElement;
    expect(await within(auditDrawer).findByText("CEILING RECLAIM")).toBeInTheDocument();
    expect(within(auditDrawer).getByText(/reclaimed the base line ₹86\.00/)).toBeInTheDocument();
  });

  it("shows compact ceiling type and containment progress", async () => {
    marketOpenMock.mockReturnValue(true);
    mockLatestScanApi({
      ...scanResponse,
      rows: [{
        ...scanResponse.rows[0],
        ceiling: { ...scanResponse.rows[0].ceiling, type: "COMPACT_RANGE" },
        rawSteps: [{
          ...scanResponse.rows[0].rawSteps[0],
          decision: "COMPACT_CEILING_CANDIDATE",
          ceilingType: null,
          compactCeilingCandidate: 85,
          compactCeilingConfirmationCount: 1,
          explanation: "₹85.00 remains a candidate after one contained session.",
        }],
      }],
    });

    render(<AdaptiveBreakoutScannerPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("leaders (1)"));
    fireEvent.click(screen.getByRole("button", { name: /Run scan/i }));

    const table = await screen.findByTestId("adaptive-breakout-table");
    expect(await within(table).findByText("Compact range · 9 sessions · 2 tests · confirmed 05 Aug")).toBeInTheDocument();
    fireEvent.click(within(table).getByRole("button", { name: "Audit ABC" }));

    const auditDrawer = document.querySelector(".adaptive-breakout-audit-drawer-root") as HTMLElement;
    expect(await within(auditDrawer).findByText("COMPACT CEILING CANDIDATE")).toBeInTheDocument();
    expect(within(auditDrawer).getByText("₹85.00 may be a compact ceiling; containment is 1/2.")).toBeInTheDocument();
    expect(within(auditDrawer).getByLabelText(
      "Down move ₹5.00, 2.50 ATR, required 0.50 ATR or ₹1.00, compact candidate; 1 of 2 contained sessions.",
    )).toHaveTextContent("contained 1/2");
  });

  it("shows an unconfirmed compact candidate in the one-page structure view", async () => {
    marketOpenMock.mockReturnValue(true);
    mockLatestScanApi({
      ...scanResponse,
      rows: [{
        ...scanResponse.rows[0],
        status: "NO_CEILING",
        ceiling: null,
        rawSteps: [{
          ...scanResponse.rows[0].rawSteps[0],
          decision: "COMPACT_CEILING_CANDIDATE",
          ceilingAnchor: null,
          ceilingUpperBoundary: null,
          ceilingTestCount: null,
          ceilingType: null,
          compactCeilingCandidate: 85,
          compactCeilingConfirmationCount: 1,
        }],
      }],
    });

    render(<AdaptiveBreakoutScannerPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("leaders (1)"));
    fireEvent.click(screen.getByRole("button", { name: /Run scan/i }));

    const table = await screen.findByTestId("adaptive-breakout-table");
    expect(await within(table).findByText("Compact candidate ₹85.00")).toBeInTheDocument();
    expect(within(table).getByText("Contained 1/2 · not active yet")).toBeInTheDocument();
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
          floorDate: "2026-07-30",
          floorPrice: 80,
          floorToBreakoutPct: 12.5,
          floorToBreakoutAtr: 4.2,
          rangeLocked: true,
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
    expect(within(table).getByText("Last fresh breakout · 05 Aug 2026")).toBeInTheDocument();
    expect(within(table).getByText("Breakout close · 05 Aug")).toBeInTheDocument();
    expect(within(table).getByText("90%")).toBeInTheDocument();
    expect(within(table).getByText("2.10×")).toBeInTheDocument();
    expect(within(table).getByText(/From floor ₹80\.00 · 30 Jul · \+12\.5% \/ 4\.2 ATR/)).toBeInTheDocument();
    expect(within(table).getByText("Locked range · next-open fill uncertain")).toBeInTheDocument();
    expect(within(table).queryByText(/Latest close/)).not.toBeInTheDocument();
  });

  it("keeps breakout evidence when the breakout candle also creates a newer ceiling", async () => {
    marketOpenMock.mockReturnValue(true);
    mockLatestScanApi({
      ...scanResponse,
      rows: [{
        ...scanResponse.rows[0],
        status: "FRESH_BREAKOUT",
        latestClose: 89,
        ceiling: {
          ...scanResponse.rows[0].ceiling,
          anchorPrice: 95,
          upperBoundary: 96,
          breakoutDate: null,
        },
        breakoutEvidence: {
          date: "2026-08-14",
          closePositionPct: 88,
          volumeVsTenDayAverage: 1.8,
          distanceFromFiftyTwoWeekHighPct: -7,
        },
      }],
    });

    render(<AdaptiveBreakoutScannerPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("leaders (1)"));
    fireEvent.click(screen.getByRole("button", { name: /Run scan/i }));

    const table = await screen.findByTestId("adaptive-breakout-table");
    expect(await within(table).findByText("Fresh breakout")).toBeInTheDocument();
    expect(within(table).getByText("Breakout close · 14 Aug")).toBeInTheDocument();
    expect(within(table).getByText("88%")).toBeInTheDocument();
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
          type: "STRONG_REJECTION",
          testCount: 1,
          lastTestDate: "2026-06-05",
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

  it("shows early breakout as a watch-only line without replacing the confirmed ceiling", async () => {
    marketOpenMock.mockReturnValue(true);
    const latestStep = scanResponse.rows[0].rawSteps.at(-1)!;
    mockLatestScanApi({
      ...scanResponse,
      rows: [{
        ...scanResponse.rows[0],
        status: "EARLY_BREAKOUT",
        latestClose: 87,
        ceiling: {
          ...scanResponse.rows[0].ceiling,
          anchorPrice: 99,
          upperBoundary: 100,
        },
        rawSteps: [{
          ...latestStep,
          close: 87,
          high: 88,
          candidatePeak: 88,
          ceilingAnchor: 99,
          ceilingUpperBoundary: 100,
          breakoutBoundary: 85.2,
          compactCeilingCandidate: 85,
          compactCeilingConfirmationCount: 0,
          decision: "EARLY_BREAKOUT",
          explanation: "Watch only: the close cleared the unconfirmed compact peak.",
        }],
      }],
    });

    render(<AdaptiveBreakoutScannerPage />);
    fireEvent.mouseDown(screen.getByRole("combobox", { name: "Watchlist" }));
    fireEvent.click(await screen.findByText("leaders (1)"));
    fireEvent.click(screen.getByRole("button", { name: /Run scan/i }));

    const table = await screen.findByTestId("adaptive-breakout-table");
    expect(await within(table).findByText("Early breakout")).toBeInTheDocument();
    expect(within(table).getByText("Early line ₹85.20")).toBeInTheDocument();
    expect(within(table).getByText(/Watch only · candidate ₹85\.00 was not confirmed/)).toBeInTheDocument();
    expect(within(table).getByText(/confirmed ceiling remains ₹100\.00/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Early breakout 1" })).toBeInTheDocument();
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
    peakRejectionAtrMultiple: 1,
    compactPeakRejectionAtrMultiple: 0.5,
    compactCeilingConfirmationSessions: 2,
    earlyBreakoutBufferAtrMultiple: 0.1,
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
      type: "STRONG_REJECTION",
      testCount: 2,
      lastTestDate: "2026-08-05",
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
        candidateFloorAtr: 2,
        candidatePeak: 85,
        candidatePeakAtr: 2,
        ceilingAnchor: 85,
        ceilingUpperBoundary: 86,
        majorCeilingUpperBoundary: null,
        ceilingTestCount: 1,
        ceilingType: "STRONG_REJECTION",
        compactCeilingCandidate: null,
        compactCeilingConfirmationCount: null,
        decision: "CEILING_CONFIRMED",
        explanation: "Price rejected ₹85.00 by at least 1.00 ATR.",
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
        candidateFloorAtr: 2,
        candidatePeak: 85,
        candidatePeakAtr: 2,
        ceilingAnchor: 85,
        ceilingUpperBoundary: 86,
        majorCeilingUpperBoundary: null,
        ceilingTestCount: 1,
        ceilingType: "STRONG_REJECTION",
        compactCeilingCandidate: null,
        compactCeilingConfirmationCount: null,
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
        candidateFloorAtr: 2,
        candidatePeak: 85,
        candidatePeakAtr: 2,
        ceilingAnchor: 85,
        ceilingUpperBoundary: 86,
        majorCeilingUpperBoundary: null,
        ceilingTestCount: 2,
        ceilingType: "STRONG_REJECTION",
        compactCeilingCandidate: null,
        compactCeilingConfirmationCount: null,
        decision: "CEILING_TEST",
        explanation: "Price returned to the confirmed ceiling.",
      },
    ],
  }],
};
