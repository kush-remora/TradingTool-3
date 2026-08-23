import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BreakoutBuyReviewPage } from "./BreakoutBuyReviewPage";

const getJsonMock = vi.fn();

vi.mock("../utils/api", () => ({
  getJson: (...args: unknown[]) => getJsonMock(...args),
}));

describe("BreakoutBuyReviewPage", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/TradingTool-3/console/breakout-buy-review?symbol=KRN&date=2026-08-11");
    getJsonMock.mockResolvedValue(report);
  });

  it("shows only raw breakout-day data", async () => {
    render(<BreakoutBuyReviewPage />);

    expect(await screen.findByText("Breakout-day raw data")).toBeInTheDocument();
    expect(getJsonMock).toHaveBeenCalledWith(
      "/api/strategy/adaptive-breakout/buy-review?symbol=KRN&date=2026-08-11",
      { useCache: false },
    );

    expect(screen.getByText("₹1,240")).toBeInTheDocument();
    expect(screen.getAllByText("₹1,287.7")).toHaveLength(2);
    expect(screen.getByText("₹1,233.4")).toBeInTheDocument();
    expect(screen.getByText("2,50,000")).toBeInTheDocument();
    expect(screen.getByText("125.0%")).toBeInTheDocument();
    expect(screen.getByText("1250.0%")).toBeInTheDocument();
    expect(screen.getByText("+20.0%")).toBeInTheDocument();
    expect(screen.getByText("+4.40%")).toBeInTheDocument();
    expect(screen.getByText("+3.85%")).toBeInTheDocument();
    expect(screen.getByText("Recent abnormal-volume days")).toBeInTheDocument();
    expect(screen.getByText("01 Aug 2026")).toBeInTheDocument();
    expect(screen.getByText("500.0%")).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Close position" })).toBeInTheDocument();
    expect(screen.getByLabelText("Close 77.6% between low and high")).toBeInTheDocument();
    expect(screen.queryByText("Fresh breakout with 4 context checks passed.")).not.toBeInTheDocument();
    expect(screen.queryByText("Quick price calculator")).not.toBeInTheDocument();
  });
});

const report = {
  symbol: "KRN",
  date: "2026-08-11",
  structureStatus: "FRESH_BREAKOUT",
  structureDecision: "FRESH_BREAKOUT",
  structureExplanation: "First close above the active ceiling.",
  overallDecision: "PASS",
  decisionSummary: "Fresh breakout with 4 context checks passed.",
  open: 1240,
  high: 1287.7,
  low: 1233.4,
  close: 1287.7,
  volume: 250000,
  twentyDayQuietAverageVolume: 20000,
  volumeVsTenDayAveragePct: 125,
  volumeVsTwentyDayQuietAveragePct: 1250,
  volumeVsPriorTwentyDayHighChangePct: 20,
  recentAbnormalVolumeDays: [
    {
      date: "2026-08-01",
      open: 1200,
      high: 1248,
      low: 1190,
      close: 1235,
      volume: 100000,
      volumeVsQuietAveragePct: 500,
      lowToHighPct: 4.87,
      openToClosePct: 2.92,
    },
  ],
  atr: 49.05,
  floor: 1150,
  peak: 1287.7,
  breakoutLine: 1254.53,
  majorCeiling: 1343.53,
  sma50: 1200,
  sma200: 975.37,
  deliveryContext: {
    deliveryPercentage: 62.5,
    deliveredQuantity: 156250,
    averageDeliveryPercentage: 54.2,
    averageDeliveredQuantity: 112000,
    deliveredQuantityMultiple: 1.4,
  },
  rules: [
    { key: "buyer-days", label: "Buyer days (5D pre-breakout)", rule: "Pass ≥4/5 days", actual: "5 / 5 sessions closed above open", verdict: "PASS", explanation: "Buyers held control." },
    { key: "close-position-trend", label: "Close position trend (5D pre-breakout)", rule: "Pass avg ≥65%", actual: "78.0% avg of 5 sessions", verdict: "PASS", explanation: "Buyers held control." },
    { key: "volume-dry-up", label: "Volume dry-up (5D vs prior 10D)", rule: "Pass ≤0.85× baseline", actual: "0.75× baseline", verdict: "PASS", explanation: "Volume contracted." },
    { key: "breakout-day-close", label: "Breakout day close position", rule: "Pass ≥75%", actual: "100% of day's range", verdict: "PASS", explanation: "Buyers held control." },
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
