import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BreakoutBuyReviewPage } from "./BreakoutBuyReviewPage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

vi.mock("../hooks/useStockDetail", () => ({
  useStockDetail: () => ({ data: null, loading: false, error: null }),
}));

describe("BreakoutBuyReviewPage", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/TradingTool-3/console/breakout-buy-review?symbol=KRN&date=2026-08-11");
    getJsonMock.mockResolvedValue(report);
  });

  it("shows the rule card before the selected-day evidence and decision", async () => {
    render(<BreakoutBuyReviewPage />);

    expect(screen.getByRole("table", { name: "Breakout day quality rules" })).toHaveTextContent("Close near the high");
    expect(screen.getByRole("table", { name: "Breakout day quality rules" })).toHaveTextContent("≥1.5×");
    expect(await screen.findByText("Fresh breakout with all five quality checks passed.")).toBeInTheDocument();
    expect(getJsonMock).toHaveBeenCalledWith(
      "/api/strategy/adaptive-breakout/buy-review?symbol=KRN&date=2026-08-11",
      { useCache: false },
    );

    const resultTable = screen.getAllByRole("table")[1];
    expect(within(resultTable).getByText("2.1×")).toBeInTheDocument();
    expect(screen.getByText("₹1,254.53")).toBeInTheDocument();
    expect(screen.getByText(/First close above the active ceiling/)).toBeInTheDocument();
    expect(screen.getByText("2 · Chart context")).toBeInTheDocument();
    expect(screen.getByText("Major ceiling")).toBeInTheDocument();
    expect(screen.getByLabelText("Next session open price")).toBeInTheDocument();
    expect(screen.getByText("Quick price calculator")).toBeInTheDocument();
  });
});

const report = {
  symbol: "KRN",
  date: "2026-08-11",
  structureStatus: "FRESH_BREAKOUT",
  structureDecision: "FRESH_BREAKOUT",
  structureExplanation: "First close above the active ceiling.",
  overallDecision: "PASS",
  decisionSummary: "Fresh breakout with all five quality checks passed.",
  open: 1240,
  high: 1287.7,
  low: 1233.4,
  close: 1287.7,
  volume: 250000,
  atr: 49.05,
  floor: 1150,
  peak: 1287.7,
  breakoutLine: 1254.53,
  majorCeiling: 1343.53,
  sma50: 1200,
  sma200: 975.37,
  deliveryPercentage: 62.5,
  deliveredQuantity: 156250,
  rules: [
    { key: "close-position", label: "Close near the high", rule: "Pass ≥80%", actual: "100% of the daily range", verdict: "PASS", explanation: "Buyers held control." },
    { key: "volume", label: "Volume vs prior 10D", rule: "Pass ≥1.5×", actual: "2.1×", verdict: "PASS", explanation: "Strong volume." },
    { key: "delivery", label: "Delivered quantity vs prior 20D", rule: "Pass ≥1.25×", actual: "1.4×", verdict: "PASS", explanation: "Strong delivery." },
    { key: "breakout-line", label: "Close above breakout line", rule: "Pass", actual: "+0.68 ATR", verdict: "PASS", explanation: "Clear close." },
    { key: "extension", label: "Close not too extended", rule: "Pass", actual: "+0.48 ATR", verdict: "PASS", explanation: "Not stretched." },
  ],
  chartContext: {
    overallDecision: "PASS",
    decisionSummary: "Trend context passes with at least 2 ATR of room to major ceiling.",
    sma50: 1200,
    sma200: 975.37,
    sma50ChangePctFiveSessions: 1.2,
    sma200ChangePctTwentySessions: 1.1,
    priorFiftyTwoWeekHigh: 1400,
    nextObstaclePrice: 1343.53,
    nextObstacleLabel: "Major ceiling",
    roomToObstaclePct: 4.34,
    roomToObstacleAtr: 2.14,
    rules: [
      { key: "price-sma50", label: "Price above 50 SMA", rule: "Pass above", actual: "+7.3%", verdict: "PASS", explanation: "Above." },
    ],
  },
};
